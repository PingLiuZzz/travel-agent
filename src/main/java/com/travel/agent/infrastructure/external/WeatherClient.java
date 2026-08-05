package com.travel.agent.infrastructure.external;

import com.travel.agent.domain.weather.WeatherInfo;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 天气服务客户端：接入 Open-Meteo（免费开源气象 API，无需 key，全球覆盖）。
 *
 * <p>查询流程：城市名 → 经纬度(geocoding) → 实时天气(forecast)。 weather_code 采用完整 WMO 标准代码表映射中文描述，并按天气类型 +
 * 温度给出个性化出行建议。 JSON 用 Map 解析（一次性结构，不为它定义 DTO，符合 KISS）。
 */
@Component
public class WeatherClient {

  private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);

  private final RestClient restClient;

  @Autowired
  public WeatherClient(RestClient restClient) {
    this.restClient = restClient;
  }

  /** 查询指定城市的实时天气。 */
  public WeatherInfo query(String city) {
    try {
      double[] coordinates = geocode(city);
      if (coordinates == null) {
        log.warn("未找到城市坐标: {}", city);
        return new WeatherInfo(city, "今天", "未知", 0, "未找到该城市");
      }
      return fetchForecast(city, coordinates[0], coordinates[1]);
    } catch (Exception exception) {
      log.error("查询天气失败: {}", city, exception);
      return new WeatherInfo(city, "今天", "查询失败", 0, "天气服务暂不可用");
    }
  }

  /** 城市名 → [纬度, 经度]，找不到返回 null。 */
  @SuppressWarnings("unchecked")
  private double[] geocode(String city) {
    Map<String, Object> resp =
        restClient
            .get()
            .uri(
                uri ->
                    uri.scheme("https")
                        .host("geocoding-api.open-meteo.com")
                        .path("/v1/search")
                        .queryParam("name", city)
                        .queryParam("count", 1)
                        .queryParam("language", "zh")
                        .build())
            .retrieve()
            .body(Map.class);
    List<Map<String, Object>> results = (List<Map<String, Object>>) resp.get("results");
    if (results == null || results.isEmpty()) {
      return null;
    }
    Map<String, Object> first = results.get(0);
    double latitude = ((Number) first.get("latitude")).doubleValue();
    double longitude = ((Number) first.get("longitude")).doubleValue();
    return new double[] {latitude, longitude};
  }

  /** 经纬度 → 实时天气。 */
  @SuppressWarnings("unchecked")
  private WeatherInfo fetchForecast(String city, double latitude, double longitude) {
    Map<String, Object> resp =
        restClient
            .get()
            .uri(
                uri ->
                    uri.scheme("https")
                        .host("api.open-meteo.com")
                        .path("/v1/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("current", "temperature_2m,weather_code")
                        .build())
            .retrieve()
            .body(Map.class);
    Map<String, Object> current = (Map<String, Object>) resp.get("current");
    int temperature = (int) Math.round(((Number) current.get("temperature_2m")).doubleValue());
    int weatherCode = ((Number) current.get("weather_code")).intValue();
    String condition = describeWeather(weatherCode);
    String tips = travelAdvice(weatherCode, temperature);
    return new WeatherInfo(city, "今天", condition, temperature, tips);
  }

  /** WMO 天气代码 → 中文描述（完整标准代码表）。 参考Open-Meteo 文档的 WMO Weather interpretation codes (WW)。 */
  private String describeWeather(int code) {
    return switch (code) {
      case 0 -> "晴朗";
      case 1 -> "主要晴朗";
      case 2 -> "部分多云";
      case 3 -> "阴天";
      case 45 -> "雾";
      case 48 -> "凝结雾";
      case 51 -> "轻度毛毛雨";
      case 53 -> "中度毛毛雨";
      case 55 -> "强毛毛雨";
      case 56 -> "轻度冻毛毛雨";
      case 57 -> "强冻毛毛雨";
      case 61 -> "小雨";
      case 63 -> "中雨";
      case 65 -> "大雨";
      case 66 -> "轻度冻雨";
      case 67 -> "强冻雨";
      case 71 -> "小雪";
      case 73 -> "中雪";
      case 75 -> "大雪";
      case 77 -> "雪粒";
      case 80 -> "小阵雨";
      case 81 -> "中阵雨";
      case 82 -> "强阵雨";
      case 85 -> "小阵雪";
      case 86 -> "强阵雪";
      case 95 -> "雷暴";
      case 96 -> "雷暴伴小冰雹";
      case 99 -> "雷暴伴强冰雹";
      default -> "未知天气";
    };
  }

  /** 根据 WMO 代码 + 温度给出个性化出行建议。 */
  private String travelAdvice(int code, int temperature) {
    // 雷暴 / 冰雹：高风险，建议避免户外
    if (code >= 95) {
      return "有雷暴或冰雹，建议避免户外活动，注意防雷安全";
    }
    // 降雨
    if (isRainCode(code)) {
      if (code == 65 || code == 67 || code == 82) {
        return "雨势较大，建议携带雨具并减少户外活动";
      }
      return "有降雨，建议携带雨具出行";
    }
    // 降雪
    if (isSnowCode(code)) {
      if (code == 75 || code == 86) {
        return "降雪较大，注意保暖防滑，谨慎出行";
      }
      return "有降雪，注意保暖和路面湿滑";
    }
    // 雾
    if (code == 45 || code == 48) {
      return "有雾，能见度低，出行注意交通安全";
    }
    // 晴 / 多云：按温度给建议
    if (temperature >= 15 && temperature <= 28) {
      return "天气舒适宜人，非常适合户外出行";
    }
    if (temperature > 28) {
      return "气温较高，注意防晒、补水，避免长时间暴晒";
    }
    return "气温较低，注意添衣保暖";
  }

  private boolean isRainCode(int code) {
    return (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
  }

  private boolean isSnowCode(int code) {
    return (code >= 71 && code <= 77) || (code >= 85 && code <= 86);
  }
}

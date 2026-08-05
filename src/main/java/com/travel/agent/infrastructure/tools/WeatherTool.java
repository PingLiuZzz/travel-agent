package com.travel.agent.infrastructure.tools;

import com.travel.agent.domain.weather.WeatherInfo;
import com.travel.agent.infrastructure.external.WeatherClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具（Function Calling）。
 *
 * <p>薄适配层：仅把领域客户端能力暴露为 Agent 可调用的工具，真实逻辑在 WeatherClient（SRP）。 LLM 根据用户意图自主决定是否调用本工具。
 */
@Component
public class WeatherTool {

  private final WeatherClient weatherClient;

  @Autowired
  public WeatherTool(WeatherClient weatherClient) {
    this.weatherClient = weatherClient;
  }

  @Tool("查询指定城市当前天气，返回天气状况、温度与出行建议")
  public WeatherInfo getWeather(String city) {
    return weatherClient.query(city);
  }
}

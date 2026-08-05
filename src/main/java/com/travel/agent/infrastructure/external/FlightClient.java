package com.travel.agent.infrastructure.external;

import com.travel.agent.domain.flight.Flight;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 航班服务客户端（一期 Mock 实现）。
 *
 * <p>调研结论：免费开放 API 中无匹配"按出发→到达+日期查航班时刻表/票价"的服务 （OpenSky 仅提供实时全球航班追踪，无航线数据）。真实接入需商业 API （携程开放平台 /
 * Amadeus，需 key + 审核），待后续提供。
 */
@Component
public class FlightClient {

  // TODO[接入真实航班 API]：替换为携程/Amadeus 的 HTTP 调用
  public List<Flight> search(String fromCity, String toCity, String date) {
    return List.of(
        new Flight("CA1234", fromCity, toCity, date + " 08:00", date + " 11:00", 1200),
        new Flight("MU5678", fromCity, toCity, date + " 14:00", date + " 17:00", 980));
  }
}

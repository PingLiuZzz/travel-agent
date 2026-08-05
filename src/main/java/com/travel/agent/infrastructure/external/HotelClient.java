package com.travel.agent.infrastructure.external;

import com.travel.agent.domain.hotel.HotelBooking;
import org.springframework.stereotype.Component;

/**
 * 酒店服务客户端（一期 Mock 实现）。
 *
 * <p>调研结论：免费开放 API 中无酒店预订/搜索服务，真实接入需商业 API （携程 / EAN，需 key + 审核），待后续提供。
 * 安全约定：一期只生成"待确认"订单，不直接扣款/锁库存。
 */
@Component
public class HotelClient {

  // TODO[接入真实酒店 API]：替换为携程/EAN 的下单调用
  public HotelBooking book(
      String city, String hotelName, String checkIn, String checkOut, String roomType) {
    // 待确认订单：必须由用户在前端二次确认后才视为有效
    String orderId = "PENDING-" + System.currentTimeMillis();
    return new HotelBooking(orderId, city, hotelName, checkIn, checkOut, roomType, "待确认");
  }
}

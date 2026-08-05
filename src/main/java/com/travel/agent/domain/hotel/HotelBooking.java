package com.travel.agent.domain.hotel;

/**
 * 酒店预订订单（不可变值对象）。
 *
 * <p>设计要点：一期只生成"待确认"订单，status 标记待确认， 由前端二次确认后才进入真实下单流程（Human-in-the-loop）。
 */
public record HotelBooking(
    String orderId,
    String city,
    String hotelName,
    String checkIn,
    String checkOut,
    String roomType,
    String status) {}

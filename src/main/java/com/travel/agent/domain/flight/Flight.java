package com.travel.agent.domain.flight;

/** 航班信息（不可变值对象）。 */
public record Flight(
    String flightNo,
    String fromCity,
    String toCity,
    String departTime,
    String arriveTime,
    int priceYuan) {}

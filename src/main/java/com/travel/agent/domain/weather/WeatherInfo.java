package com.travel.agent.domain.weather;

/**
 * 天气信息（不可变值对象）。
 *
 * <p>由天气工具返回，LangChain4j 会将其序列化为 JSON 供 LLM 理解。
 */
public record WeatherInfo(
    String city, String date, String condition, int temperatureCelsius, String tips) {}

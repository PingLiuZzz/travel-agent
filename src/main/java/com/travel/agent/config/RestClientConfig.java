package com.travel.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** HTTP 客户端装配：用于调用外部开放 API（如 Open-Meteo 天气）。 使用 Spring Boot 3.4 提供的 RestClient.Builder。 */
@Configuration
public class RestClientConfig {

  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    return builder.build();
  }
}

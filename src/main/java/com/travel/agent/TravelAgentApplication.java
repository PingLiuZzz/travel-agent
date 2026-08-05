package com.travel.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 旅游出行智能体启动类。
 *
 * <p>开启 {@link ConfigurationPropertiesScan} 以自动注册各 @ConfigurationProperties 配置类。
 *
 * <p>{@link EnableAsync} 为后续异步落库任务预留（如对话消息异步持久化）。
 *
 * <p>{@link MapperScan} 扫描持久层 Mapper 接口包，由 MyBatis-Plus 代理实现。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@MapperScan("com.travel.agent.infrastructure.persistence.mapper")
public class TravelAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(TravelAgentApplication.class, args);
  }
}

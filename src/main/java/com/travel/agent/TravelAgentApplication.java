package com.travel.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 旅游出行智能体启动类。
 *
 * <p>开启 {@link ConfigurationPropertiesScan} 以自动注册各 @ConfigurationProperties 配置类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TravelAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelAgentApplication.class, args);
    }
}

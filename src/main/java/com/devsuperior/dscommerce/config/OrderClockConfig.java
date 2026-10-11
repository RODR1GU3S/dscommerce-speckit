package com.devsuperior.dscommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Production time source required by OrderService; tests can inject their existing primary Clock. */
@Configuration(proxyBeanMethods = false)
public class OrderClockConfig {

    @Bean
    public Clock orderClock() {
        return Clock.systemUTC();
    }
}

package com.inmobiliaria.user_service.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class NotificationFeignConfig {

  @Bean
  public RequestInterceptor notificationRequestInterceptor() {
    return template -> template.header("X-Service-Name", "user-service");
  }
}

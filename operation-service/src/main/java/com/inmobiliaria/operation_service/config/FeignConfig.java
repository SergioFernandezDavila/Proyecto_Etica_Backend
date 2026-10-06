package com.inmobiliaria.operation_service.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

  @Bean
  public RequestInterceptor requestInterceptor() {
    return requestTemplate -> {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
          requestTemplate.header("Authorization", authHeader);
        }

        String userIdHeader = request.getHeader("X-Auth-User-Id");
        if (userIdHeader != null) {
          requestTemplate.header("X-Auth-User-Id", userIdHeader);
        }

        String rolesHeader = request.getHeader("X-Auth-Roles");
        if (rolesHeader != null) {
          requestTemplate.header("X-Auth-Roles", rolesHeader);
        }
      }
    };
  }
}

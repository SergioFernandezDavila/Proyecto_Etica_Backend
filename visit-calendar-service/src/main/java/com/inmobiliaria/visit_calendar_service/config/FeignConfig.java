package com.inmobiliaria.visit_calendar_service.config;

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

        // Propagate standard Auth headers
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

        // Also propagate X-Agent-Id if present (specific to visit-calendar-service)
        String agentIdHeader = request.getHeader("X-Agent-Id");
        if (agentIdHeader != null) {
          requestTemplate.header("X-Agent-Id", agentIdHeader);
        }
      }
      requestTemplate.header("X-Service-Name", "visit-calendar-service");
    };
  }
}

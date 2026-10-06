package com.inmobiliaria.property_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {
  @Bean
  public RequestInterceptor requestInterceptor() {
    return template -> {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
          template.header("Authorization", authHeader);
        }

        template.header("X-Auth-User-Id", request.getHeader("X-Auth-User-Id"));

        // Propagate roles but strip ROLE_ prefix to maintain a single contract
        String rolesHeader = request.getHeader("X-Auth-Roles");
        if (rolesHeader != null) {
          String cleanRoles = rolesHeader.replace("ROLE_", "");
          template.header("X-Auth-Roles", cleanRoles);
        }
      }
    };
  }

  @Bean
  public Decoder feignDecoder(ObjectMapper objectMapper) {
    return new StandardApiResponseDecoder(objectMapper);
  }
}

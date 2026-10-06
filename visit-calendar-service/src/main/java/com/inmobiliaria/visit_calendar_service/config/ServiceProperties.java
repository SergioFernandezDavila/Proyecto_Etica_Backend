package com.inmobiliaria.visit_calendar_service.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public record ServiceProperties(
    UserService user,
    PersonService person,
    PropertyService property,
    NotificationService notification,
    AppProperties app) {

  public record UserService(ServiceUrl service) {}

  public record PersonService(ServiceUrl service) {}

  public record PropertyService(ServiceUrl service) {}

  public record NotificationService(ServiceUrl service) {}

  public record ServiceUrl(String url) {}

  public record AppProperties(CorsProperties cors) {}

  public record CorsProperties(List<String> allowedOrigins) {}
}

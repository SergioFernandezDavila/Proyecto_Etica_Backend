package com.inmobiliaria.visit_calendar_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configuración CORS para permitir peticiones desde el frontend Vue. */
@Configuration
public class CorsConfig {

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      // CORS manejado por el api-gateway, no configurar aquí
    };
  }
}

package com.inmobiliaria.property_service;

import com.inmobiliaria.property_service.config.MinioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(MinioProperties.class)
public class PropertyServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PropertyServiceApplication.class, args);
  }
}

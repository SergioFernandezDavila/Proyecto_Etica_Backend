package com.inmobiliaria.operation_service;

import com.inmobiliaria.operation_service.config.MinioProperties;
import com.inmobiliaria.operation_service.config.ReceiptProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Operation Service — Inmobiliaria
 *
 * <p>Manages real-estate operations and their associated payment receipts. Receipts (PDF / images)
 * are stored in MinIO object storage.
 *
 * <p>Port : 8087 Eureka: registered as "operation-service"
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableConfigurationProperties({MinioProperties.class, ReceiptProperties.class})
public class OperationServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(OperationServiceApplication.class, args);
  }
}

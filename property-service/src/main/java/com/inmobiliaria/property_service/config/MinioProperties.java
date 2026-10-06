package com.inmobiliaria.property_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
    String endpoint,
    String externalEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    DocumentsProperties documents) {
  public record DocumentsProperties(String bucket) {}
}

package com.inmobiliaria.operation_service.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipt")
public record ReceiptProperties(UploadProperties upload) {
  public record UploadProperties(List<String> allowedTypes, long maxSizeBytes) {}
}

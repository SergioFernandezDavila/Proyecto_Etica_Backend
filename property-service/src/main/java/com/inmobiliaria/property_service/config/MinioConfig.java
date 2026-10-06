package com.inmobiliaria.property_service.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MinioConfig {

  @Autowired private MinioProperties minioProperties;

  @Bean
  @Primary
  public MinioClient minioClient() {
    // Internal client uses docker hostname (minio:9000)
    return MinioClient.builder()
        .endpoint(minioProperties.endpoint())
        .credentials(minioProperties.accessKey(), minioProperties.secretKey())
        .build();
  }

  @Bean(name = "externalMinioClient")
  public MinioClient externalMinioClient() {
    // External client uses external hostname (127.0.0.1:9000) and EXPLICIT REGION
    // Explicit region prevents connection attempts during pre-signed URL generation
    return MinioClient.builder()
        .endpoint(minioProperties.externalEndpoint())
        .region("us-east-1")
        .credentials(minioProperties.accessKey(), minioProperties.secretKey())
        .build();
  }

  public String getBucket() {
    return minioProperties.bucket();
  }

  public String getDocumentsBucket() {
    return minioProperties.documents().bucket();
  }
}

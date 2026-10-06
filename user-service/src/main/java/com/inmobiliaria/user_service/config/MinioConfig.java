package com.inmobiliaria.user_service.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class MinioConfig {

  @Autowired private MinioProperties minioProperties;

  @Bean
  @Primary
  public MinioClient minioClient() {
    MinioClient client =
        MinioClient.builder()
            .endpoint(minioProperties.endpoint())
            .credentials(minioProperties.accessKey(), minioProperties.secretKey())
            .build();

    ensureBucketExists(client);
    return client;
  }

  @Bean(name = "externalMinioClient")
  public MinioClient externalMinioClient() {
    return MinioClient.builder()
        .endpoint(minioProperties.externalEndpoint())
        .region("us-east-1")
        .credentials(minioProperties.accessKey(), minioProperties.secretKey())
        .build();
  }

  private void ensureBucketExists(MinioClient client) {
    try {
      boolean exists =
          client.bucketExists(
              BucketExistsArgs.builder().bucket(minioProperties.bucketName()).build());

      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.bucketName()).build());
        log.info("[MinIO] Bucket '{}' created successfully.", minioProperties.bucketName());
      } else {
        log.info("[MinIO] Bucket '{}' already exists.", minioProperties.bucketName());
      }
    } catch (Exception e) {
      log.error(
          "[MinIO] Failed to verify/create bucket '{}': {}",
          minioProperties.bucketName(),
          e.getMessage());
    }
  }
}

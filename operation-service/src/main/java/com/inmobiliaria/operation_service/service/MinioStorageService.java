package com.inmobiliaria.operation_service.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MinioStorageService {

  private final MinioClient minioClient;
  private final MinioClient externalMinioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  private static final int PRESIGNED_URL_EXPIRY_HOURS = 1;

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

  private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

  public MinioStorageService(
      MinioClient minioClient, @Qualifier("externalMinioClient") MinioClient externalMinioClient) {
    this.minioClient = minioClient;
    this.externalMinioClient = externalMinioClient;
  }

  public String uploadFile(MultipartFile file, String operationId) {
    validateFile(file);

    String objectKey = buildObjectKey(operationId, file.getOriginalFilename());

    try (InputStream inputStream = file.getInputStream()) {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectKey).stream(
                  inputStream, file.getSize(), -1)
              .contentType(file.getContentType())
              .build());
      log.info("[MinIO] File uploaded successfully: bucket='{}', key='{}'", bucketName, objectKey);
      return objectKey;

    } catch (Exception e) {
      log.error("[MinIO] Upload failed for key '{}': {}", objectKey, e.getMessage());
      throw new RuntimeException("File upload to MinIO failed: " + e.getMessage(), e);
    }
  }

  public String generatePresignedUrl(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) return null;
    try {
      // Signing uses external client (Silent signing)
      return externalMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucketName)
              .object(objectKey)
              .expiry(PRESIGNED_URL_EXPIRY_HOURS, TimeUnit.HOURS)
              .build());
    } catch (Exception e) {
      log.warn(
          "[MinIO] Failed to generate pre-signed URL for key '{}': {}", objectKey, e.getMessage());
      return null;
    }
  }

  public void deleteFile(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) return;
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object(objectKey).build());
      log.info("[MinIO] File deleted successfully: key='{}'", objectKey);
    } catch (Exception e) {
      log.warn("[MinIO] Failed to delete file '{}': {}", objectKey, e.getMessage());
    }
  }

  public void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("No file was provided or the file is empty.");
    }

    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new IllegalArgumentException(
          "Invalid file format. Only PDF, JPEG, PNG, and WebP files are accepted. "
              + "Received: "
              + contentType);
    }

    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new IllegalArgumentException(
          "File is too large. Maximum allowed size is 10 MB. "
              + "Received: "
              + (file.getSize() / (1024 * 1024))
              + " MB.");
    }
  }

  private String buildObjectKey(String operationId, String originalFilename) {
    String extension = "";
    if (originalFilename != null && originalFilename.contains(".")) {
      extension = "." + originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }
    return "receipts/" + operationId + "/" + UUID.randomUUID() + extension;
  }
}

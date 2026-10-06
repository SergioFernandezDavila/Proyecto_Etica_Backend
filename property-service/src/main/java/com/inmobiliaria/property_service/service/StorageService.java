package com.inmobiliaria.property_service.service;

import java.util.Map;

public interface StorageService {
  /**
   * Generates a presigned POST policy for uploading a file directly to the storage provider.
   *
   * @param bucket Name of the bucket
   * @param objectKey The path/key of the object
   * @param contentType Expected MIME type
   * @param maxSizeBytes Maximum allowed size in bytes
   * @param expiryMinutes Expiration time in minutes
   * @return A map containing the upload URL and the required form-data fields
   */
  Map<String, Object> generateUploadPolicy(
      String bucket, String objectKey, String contentType, long maxSizeBytes, int expiryMinutes);

  /**
   * Gets the metadata of an object.
   *
   * @param bucket Name of the bucket
   * @param objectKey The path/key of the object
   * @return A map containing metadata like size and content-type
   */
  Map<String, Object> getObjectMetadata(String bucket, String objectKey);

  /** Deletes an object from storage. */
  void deleteObject(String bucket, String objectKey);

  /** Moves an object from one key to another within the same bucket. */
  void moveObject(String bucket, String sourceKey, String destinationKey);

  /** Generates a temporary GET URL for an object. */
  String generateTemporaryUrl(String bucket, String objectKey, int expiryMinutes);

  /** Generates a presigned PUT URL for uploading directly to an object key. */
  String generatePresignedPutUrl(String bucket, String objectKey, int expiryMinutes);

  /** Uploads a file directly from an input stream. */
  void uploadObject(
      String bucket,
      String objectKey,
      java.io.InputStream inputStream,
      long size,
      String contentType);

  /** Ensures the bucket exists. */
  void ensureBucketExists(String bucket);
}

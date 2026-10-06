package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.domain.DocumentMetadata;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.dto.request.ConfirmUploadRequest;
import com.inmobiliaria.property_service.dto.request.GenerateUploadUrlRequest;
import com.inmobiliaria.property_service.dto.response.DocumentResponse;
import com.inmobiliaria.property_service.exception.AccessDeniedException;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.exception.ValidationException;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentService {

  private final MinioClient minioClient;
  private final MinioClient externalMinioClient;
  private final PropertyRepository propertyRepository;

  @Value("${minio.presigned.expiry-minutes:15}")
  private int presignedExpiryMinutes;

  @Value("${minio.documents.bucket:documents}")
  private String documentsBucket;

  @Value("${minio.external-endpoint:http://127.0.0.1:9000}")
  private String externalEndpoint;

  public DocumentService(
      MinioClient minioClient,
      @Qualifier("externalMinioClient") MinioClient externalMinioClient,
      PropertyRepository propertyRepository) {
    this.minioClient = minioClient;
    this.externalMinioClient = externalMinioClient;
    this.propertyRepository = propertyRepository;
  }

  // Allowed file types for exclusivity contracts
  private static final Set<String> ALLOWED_MIME_TYPES =
      Set.of(
          "application/pdf",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

  public Map<String, String> generatePresignedUploadUrl(GenerateUploadUrlRequest request) {
    if (!isValidFileType(request.getMimeType(), request.getFileName())) {
      throw new ValidationException("Invalid file type. Only PDF and Word documents are allowed.");
    }

    if (request.getFileSize() > MAX_FILE_SIZE) {
      throw new ValidationException("File size exceeds limit.");
    }

    PropertyDocument property =
        propertyRepository
            .findById(request.getPropertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

    String currentUserId = getCurrentUserId();
    List<String> roles = getCurrentUserRoles();
    boolean isAdmin = roles.contains("ROLE_ADMIN");
    boolean isAssignedAgent =
        property.getAssignedAgentId() != null
            && property.getAssignedAgentId().equals(currentUserId);

    if (!isAdmin && !isAssignedAgent) {
      throw new AccessDeniedException("Permission denied");
    }

    ensureDocumentsBucketExists();
    String objectKey =
        buildDocumentObjectKey(
            request.getPropertyId(), request.getDocumentType(), request.getFileName());

    try {
      // Signing uses external client (Silent signing)
      String uploadUrl =
          externalMinioClient.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(documentsBucket)
                  .object(objectKey)
                  .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
                  .build());

      log.info(
          "Generated presigned upload URL for property {}: {}", request.getPropertyId(), objectKey);

      return Map.of(
          "uploadUrl",
          uploadUrl,
          "objectKey",
          objectKey,
          "publicUrl",
          getPublicUrl(objectKey),
          "expiresInSeconds",
          String.valueOf(presignedExpiryMinutes * 60));
    } catch (Exception e) {
      log.error("Error generating presigned URL: {}", e.getMessage());
      throw new RuntimeException("Failed to generate upload URL", e);
    }
  }

  public DocumentResponse confirmUpload(ConfirmUploadRequest request) {
    PropertyDocument property =
        propertyRepository
            .findById(request.getPropertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

    try {
      // Internal operation uses internal client
      minioClient.statObject(
          StatObjectArgs.builder().bucket(documentsBucket).object(request.getObjectKey()).build());
    } catch (Exception e) {
      throw new ValidationException("File upload not confirmed.");
    }

    DocumentMetadata document =
        DocumentMetadata.builder()
            .id(UUID.randomUUID().toString())
            .documentType(request.getDocumentType())
            .originalFileName(request.getOriginalFileName())
            .objectKey(request.getObjectKey())
            .publicUrl(getPublicUrl(request.getObjectKey()))
            .fileSize(request.getFileSize())
            .mimeType(request.getMimeType())
            .uploadedAt(Instant.now())
            .uploadedBy(getCurrentUserId())
            .uploadedByName(getCurrentUserName())
            .status(DocumentMetadata.DocumentStatus.PENDING)
            .accessPolicy(new HashSet<>())
            .build();

    if (property.getDocuments() == null) {
      property.setDocuments(new ArrayList<>());
    }
    property.getDocuments().add(document);
    property.setUpdatedAt(Instant.now());
    PropertyDocument saved = propertyRepository.save(property);

    DocumentMetadata savedDoc =
        saved.getDocuments().stream()
            .filter(d -> d.getId().equals(document.getId()))
            .findFirst()
            .orElseThrow();

    return toResponse(savedDoc, null);
  }

  public List<DocumentResponse> getPropertyDocuments(String propertyId) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

    checkDocumentAccessPermission(property, null);

    List<DocumentResponse> responses = new ArrayList<>();
    for (DocumentMetadata doc : property.getDocuments()) {
      String tempUrl = generateTemporaryDownloadUrlForDocument(doc, property);
      responses.add(toResponse(doc, tempUrl));
    }
    return responses;
  }

  public DocumentResponse getDocument(String documentId) {
    for (PropertyDocument property : propertyRepository.findAll()) {
      if (property.getDocuments() != null) {
        Optional<DocumentMetadata> docOpt =
            property.getDocuments().stream().filter(d -> d.getId().equals(documentId)).findFirst();
        if (docOpt.isPresent()) {
          DocumentMetadata doc = docOpt.get();
          checkDocumentAccessPermission(property, doc);
          String tempUrl = generateTemporaryDownloadUrlForDocument(doc, property);
          return toResponse(doc, tempUrl);
        }
      }
    }
    throw new ResourceNotFoundException("Document not found");
  }

  public DocumentResponse updateDocumentPermissions(String documentId, Set<String> accessPolicy) {
    for (PropertyDocument property : propertyRepository.findAll()) {
      if (property.getDocuments() != null) {
        Optional<DocumentMetadata> docOpt =
            property.getDocuments().stream().filter(d -> d.getId().equals(documentId)).findFirst();
        if (docOpt.isPresent()) {
          DocumentMetadata doc = docOpt.get();
          doc.setAccessPolicy(accessPolicy != null ? accessPolicy : new HashSet<>());
          property.setUpdatedAt(Instant.now());
          propertyRepository.save(property);
          return getDocument(documentId);
        }
      }
    }
    throw new ResourceNotFoundException("Document not found");
  }

  public String generateTemporaryDownloadUrl(String documentId) {
    for (PropertyDocument property : propertyRepository.findAll()) {
      if (property.getDocuments() != null) {
        Optional<DocumentMetadata> docOpt =
            property.getDocuments().stream().filter(d -> d.getId().equals(documentId)).findFirst();
        if (docOpt.isPresent()) {
          DocumentMetadata doc = docOpt.get();
          checkDocumentAccessPermission(property, doc);
          return generateTemporaryDownloadUrlForDocument(doc, property);
        }
      }
    }
    throw new ResourceNotFoundException("Document not found");
  }

  public void deleteDocument(String documentId) {
    for (PropertyDocument property : propertyRepository.findAll()) {
      if (property.getDocuments() != null) {
        Optional<DocumentMetadata> docOpt =
            property.getDocuments().stream().filter(d -> d.getId().equals(documentId)).findFirst();
        if (docOpt.isPresent()) {
          DocumentMetadata doc = docOpt.get();
          try {
            minioClient.removeObject(
                io.minio.RemoveObjectArgs.builder()
                    .bucket(documentsBucket)
                    .object(doc.getObjectKey())
                    .build());
          } catch (Exception e) {
            log.error("Failed to delete file from MinIO");
          }
          property.getDocuments().removeIf(d -> d.getId().equals(documentId));
          property.setUpdatedAt(Instant.now());
          propertyRepository.save(property);
          return;
        }
      }
    }
    throw new ResourceNotFoundException("Document not found");
  }

  private void checkDocumentAccessPermission(PropertyDocument property, DocumentMetadata document) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new AccessDeniedException("Authentication required");
    }
    String userId = getCurrentUserId();
    Set<String> roles = getCurrentUserRolesSet();
    if (roles.contains("ROLE_ADMIN")) return;
    if (property.getAssignedAgentId() != null && property.getAssignedAgentId().equals(userId))
      return;
    if (property.getOwnerId() != null && property.getOwnerId().equals(userId)) return;
    throw new AccessDeniedException("Permission denied");
  }

  private String generateTemporaryDownloadUrlForDocument(
      DocumentMetadata document, PropertyDocument property) {
    try {
      // Signing uses external client
      return externalMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(documentsBucket)
              .object(document.getObjectKey())
              .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
              .build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate download URL", e);
    }
  }

  public int getPresignedExpirySeconds() {
    return presignedExpiryMinutes * 60;
  }

  private boolean isValidFileType(String mimeType, String fileName) {
    if (mimeType != null && ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) return true;
    String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    return Set.of("pdf", "doc", "docx").contains(ext);
  }

  private String buildDocumentObjectKey(String propertyId, String documentType, String fileName) {
    String timestamp = String.valueOf(Instant.now().toEpochMilli());
    String safeFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    return String.format(
        "properties/%s/%s/%s_%s", propertyId, documentType.toLowerCase(), timestamp, safeFileName);
  }

  private String getPublicUrl(String objectKey) {
    return String.format("%s/%s/%s", externalEndpoint, documentsBucket, objectKey);
  }

  private void ensureDocumentsBucketExists() {
    try {
      boolean found =
          minioClient.bucketExists(
              io.minio.BucketExistsArgs.builder().bucket(documentsBucket).build());
      if (!found) {
        minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(documentsBucket).build());
      }
    } catch (Exception e) {
      log.error("Error ensuring documents bucket exists");
    }
  }

  private String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null ? (String) auth.getPrincipal() : "unknown";
  }

  private List<String> getCurrentUserRoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return List.of();
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());
  }

  private Set<String> getCurrentUserRolesSet() {
    return new HashSet<>(getCurrentUserRoles());
  }

  private String getCurrentUserName() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null ? (String) auth.getPrincipal() : "unknown";
  }

  private DocumentResponse toResponse(DocumentMetadata document, String temporaryUrl) {
    return DocumentResponse.builder()
        .id(document.getId())
        .documentType(document.getDocumentType())
        .originalFileName(document.getOriginalFileName())
        .objectKey(document.getObjectKey())
        .publicUrl(document.getPublicUrl())
        .fileSize(document.getFileSize())
        .mimeType(document.getMimeType())
        .uploadedAt(document.getUploadedAt())
        .uploadedBy(document.getUploadedBy())
        .uploadedByName(document.getUploadedByName())
        .status(document.getStatus())
        .accessPolicy(document.getAccessPolicy())
        .validUntil(document.getValidUntil())
        .temporaryDownloadUrl(temporaryUrl)
        .expiresInSeconds(presignedExpiryMinutes * 60)
        .build();
  }
}

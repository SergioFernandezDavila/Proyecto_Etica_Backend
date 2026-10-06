package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.domain.ImageMetadata;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.domain.PropertyStatus;
import com.inmobiliaria.property_service.dto.request.GenerateImageUploadUrlRequest;
import com.inmobiliaria.property_service.dto.response.ImageResponse;
import com.inmobiliaria.property_service.dto.response.ImageUploadPolicyResponse;
import com.inmobiliaria.property_service.exception.AccessDeniedException;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.exception.ValidationException;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

  private final StorageService storageService;
  private final PropertyRepository propertyRepository;

  @Value("${minio.presigned.expiry-minutes:15}")
  private int presignedExpiryMinutes;

  @Value("${minio.images.bucket:property-images}")
  private String imagesBucket;

  private static final Set<String> ALLOWED_IMAGE_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

  private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
  private static final int MAX_IMAGES_PER_PROPERTY = 20;

  public ImageUploadPolicyResponse generateUploadPolicy(GenerateImageUploadUrlRequest request) {
    if (!isValidImageType(request.getMimeType(), request.getFileName())) {
      throw new ValidationException(
          "Invalid image type. Only JPG, PNG, WebP, and HEIC are allowed.");
    }

    PropertyDocument property =
        propertyRepository
            .findById(request.getPropertyId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Property not found: " + request.getPropertyId()));

    if (property.getImages() != null && property.getImages().size() >= MAX_IMAGES_PER_PROPERTY) {
      throw new ValidationException(
          String.format("Maximum %d images per property reached", MAX_IMAGES_PER_PROPERTY));
    }

    String currentUserId = getCurrentUserId();
    List<String> roles = getCurrentUserRoles();
    boolean isAdmin = roles.contains("ROLE_ADMIN");
    boolean isAssignedAgent =
        property.getAssignedAgentId() != null
            && property.getAssignedAgentId().equals(currentUserId);

    if (!isAdmin && !isAssignedAgent) {
      throw new AccessDeniedException(
          "You don't have permission to upload images for this property");
    }

    String objectKey = buildImageObjectKey(request.getPropertyId(), request.getFileName(), true);

    Map<String, Object> policyData =
        storageService.generateUploadPolicy(
            imagesBucket, objectKey, request.getMimeType(), MAX_IMAGE_SIZE, presignedExpiryMinutes);

    @SuppressWarnings("unchecked")
    Map<String, String> formData = (Map<String, String>) policyData.get("formData");

    return ImageUploadPolicyResponse.builder()
        .url((String) policyData.get("url"))
        .objectKey(objectKey)
        .formData(formData)
        .expiresInSeconds(presignedExpiryMinutes * 60)
        .build();
  }

  public Map<String, String> generatePresignedUploadUrl(GenerateImageUploadUrlRequest request) {
    if (!isValidImageType(request.getMimeType(), request.getFileName())) {
      throw new ValidationException(
          "Invalid image type. Only JPG, PNG, WebP, and HEIC are allowed.");
    }

    String objectKey = buildImageObjectKey(request.getPropertyId(), request.getFileName(), true);
    String uploadUrl =
        storageService.generatePresignedPutUrl(imagesBucket, objectKey, presignedExpiryMinutes);

    Map<String, String> data = new HashMap<>();
    data.put("uploadUrl", uploadUrl);
    data.put("objectKey", objectKey);
    data.put("publicUrl", getPublicUrl(objectKey));
    data.put("expiresInSeconds", String.valueOf(presignedExpiryMinutes * 60));
    return data;
  }

  public Map<String, String> generatePresignedUploadUrl(String propertyId, String fileName) {
    GenerateImageUploadUrlRequest request = new GenerateImageUploadUrlRequest();
    request.setPropertyId(propertyId);
    request.setFileName(fileName);
    request.setFileSize(0L);
    return generatePresignedUploadUrl(request);
  }

  public String uploadImageDirectly(String propertyId, MultipartFile file) {
    try {
      ensureImagesBucketExists();

      String originalFilename = file.getOriginalFilename();
      String safeFileName = originalFilename != null ? originalFilename : "image.jpg";
      String objectKey = buildImageObjectKey(propertyId, safeFileName);

      try (InputStream inputStream = file.getInputStream()) {
        storageService.uploadObject(
            imagesBucket, objectKey, inputStream, file.getSize(), file.getContentType());
      }

      String publicUrl = getPublicUrl(objectKey);
      log.info("Image uploaded directly for property {}: {}", propertyId, publicUrl);

      return publicUrl;
    } catch (Exception e) {
      log.error("Error uploading image for property {}: {}", propertyId, e.getMessage());
      throw new RuntimeException("Failed to upload image", e);
    }
  }

  public PropertyDocument confirmImageUpload(
      String propertyId,
      String objectKey,
      String originalFileName,
      Long fileSize,
      String mimeType,
      Boolean isPrimary) {

    // Strict key scoping validation
    String expectedPrefix = String.format("properties/%s/images/pending/", propertyId);
    if (!objectKey.startsWith(expectedPrefix)) {
      throw new ValidationException(
          "Invalid object key for this property or file already confirmed");
    }

    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

    // Retrieve true metadata from storage instead of trusting client
    Map<String, Object> metadata = storageService.getObjectMetadata(imagesBucket, objectKey);
    Long trueSize = (Long) metadata.get("size");
    String trueMimeType = (String) metadata.get("contentType");

    if (trueSize > MAX_IMAGE_SIZE) {
      storageService.deleteObject(imagesBucket, objectKey);
      throw new ValidationException("Uploaded file exceeds size limit");
    }

    // Move from pending to confirmed folder
    String finalObjectKey = objectKey.replace("/pending/", "/");
    storageService.moveObject(imagesBucket, objectKey, finalObjectKey);

    String currentUserId = getCurrentUserId();
    String currentUserName = getCurrentUserName();

    int nextOrder = property.getImages() != null ? property.getImages().size() : 0;

    if (Boolean.TRUE.equals(isPrimary)) {
      if (property.getImages() != null) {
        property.getImages().forEach(img -> img.setIsPrimary(false));
      }
    } else if (property.getImages() == null || property.getImages().isEmpty()) {
      isPrimary = true;
    }

    ImageMetadata image =
        ImageMetadata.builder()
            .id(UUID.randomUUID().toString())
            .originalFileName(originalFileName)
            .objectKey(finalObjectKey)
            .publicUrl(getPublicUrl(finalObjectKey))
            .fileSize(trueSize)
            .mimeType(trueMimeType)
            .isPrimary(isPrimary != null ? isPrimary : false)
            .displayOrder(nextOrder)
            .uploadedAt(Instant.now())
            .uploadedBy(currentUserId)
            .uploadedByName(currentUserName)
            .status(ImageMetadata.ImageStatus.ACTIVE)
            .accessPolicy(new HashSet<>())
            .build();

    if (property.getImages() == null) {
      property.setImages(new ArrayList<>());
    }
    property.getImages().add(image);
    property.setUpdatedAt(Instant.now());

    return propertyRepository.save(property);
  }

  public List<ImageResponse> getPropertyImages(String propertyId) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

    checkImageAccessPermission(property, null);

    List<ImageResponse> responses = new ArrayList<>();
    if (property.getImages() != null) {
      for (ImageMetadata image : property.getImages()) {
        if (image.getStatus() == ImageMetadata.ImageStatus.ACTIVE) {
          String tempUrl = generateTemporaryImageUrl(image);
          responses.add(toImageResponse(image, tempUrl));
        }
      }
    }

    responses.sort(
        (a, b) -> {
          if (a.getIsPrimary() && !b.getIsPrimary()) return -1;
          if (!a.getIsPrimary() && b.getIsPrimary()) return 1;
          return Integer.compare(a.getDisplayOrder(), b.getDisplayOrder());
        });

    return responses;
  }

  public ImageResponse setPrimaryImage(String propertyId, String imageId) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

    if (property.getImages() == null) {
      throw new ResourceNotFoundException("No images found for this property");
    }

    ImageMetadata targetImage = null;
    for (ImageMetadata img : property.getImages()) {
      if (img.getId().equals(imageId)) {
        targetImage = img;
        img.setIsPrimary(true);
      } else {
        img.setIsPrimary(false);
      }
    }

    if (targetImage == null) {
      throw new ResourceNotFoundException("Image not found: " + imageId);
    }

    property.setUpdatedAt(Instant.now());
    propertyRepository.save(property);

    String tempUrl = generateTemporaryImageUrl(targetImage);
    return toImageResponse(targetImage, tempUrl);
  }

  public List<ImageResponse> reorderImages(String propertyId, List<String> orderedImageIds) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

    if (property.getImages() == null || property.getImages().isEmpty()) {
      throw new ValidationException("No images to reorder");
    }

    Map<String, ImageMetadata> imageMap =
        property.getImages().stream().collect(Collectors.toMap(ImageMetadata::getId, img -> img));

    List<ImageMetadata> reordered = new ArrayList<>();
    for (int i = 0; i < orderedImageIds.size(); i++) {
      String id = orderedImageIds.get(i);
      ImageMetadata img = imageMap.get(id);
      if (img != null) {
        img.setDisplayOrder(i);
        img.setIsPrimary(i == 0); // The first image becomes primary
        reordered.add(img);
        imageMap.remove(id);
      }
    }

    int remainingIndex = reordered.size();
    for (ImageMetadata img : imageMap.values()) {
      img.setDisplayOrder(remainingIndex++);
      img.setIsPrimary(reordered.isEmpty());
      reordered.add(img);
    }
    property.setImages(reordered);
    property.setUpdatedAt(Instant.now());
    propertyRepository.save(property);

    return getPropertyImages(propertyId);
  }

  public void deleteImage(String propertyId, String imageId) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

    if (property.getImages() == null) {
      throw new ResourceNotFoundException("Image not found");
    }

    ImageMetadata toDelete =
        property.getImages().stream()
            .filter(i -> i.getId().equals(imageId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

    try {
      storageService.deleteObject(imagesBucket, toDelete.getObjectKey());
      log.info("Deleted image file from storage: {}", toDelete.getObjectKey());
    } catch (Exception e) {
      log.error("Failed to delete image from storage: {}", e.getMessage());
    }

    property.getImages().removeIf(i -> i.getId().equals(imageId));

    if (toDelete.getIsPrimary()
        && property.getImages() != null
        && !property.getImages().isEmpty()) {
      property.getImages().get(0).setIsPrimary(true);
    }

    for (int i = 0; i < property.getImages().size(); i++) {
      property.getImages().get(i).setDisplayOrder(i);
    }

    property.setUpdatedAt(Instant.now());
    propertyRepository.save(property);

    log.info("Deleted image: {} from property {}", imageId, propertyId);
  }

  public void deleteAllImagesForProperty(String propertyId) {
    PropertyDocument property = propertyRepository.findById(propertyId).orElse(null);

    if (property != null && property.getImages() != null) {
      for (ImageMetadata image : property.getImages()) {
        storageService.deleteObject(imagesBucket, image.getObjectKey());
      }
      log.info("Deleted {} images for property {}", property.getImages().size(), propertyId);
    }
  }

  public PropertyDocument confirmImageUpload(String propertyId, String objectKey) {
    return confirmImageUpload(propertyId, objectKey, null, null, null, null);
  }

  public String generateTemporaryImageUrl(ImageMetadata image) {
    try {
      return storageService.generateTemporaryUrl(
          imagesBucket, image.getObjectKey(), presignedExpiryMinutes);
    } catch (Exception e) {
      log.error("Error generating image URL: {}", e.getMessage());
      return image.getPublicUrl();
    }
  }

  private void checkImageAccessPermission(PropertyDocument property, ImageMetadata image) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new AccessDeniedException("Authentication required");
    }

    String userId = getCurrentUserId();
    Set<String> roles = getCurrentUserRolesSet();

    if (roles.contains("ROLE_ADMIN")) {
      return;
    }

    boolean isAssignedAgent =
        property.getAssignedAgentId() != null && property.getAssignedAgentId().equals(userId);
    if (isAssignedAgent) {
      return;
    }

    boolean isOwner = property.getOwnerId() != null && property.getOwnerId().equals(userId);
    if (isOwner) {
      return;
    }

    if (roles.contains("ROLE_AGENT")
        || roles.contains("ROLE_CLIENT")
        || roles.contains("ROLE_USER")) {
      return;
    }

    // Public visibility: if the property is in an active state, images are accessible to all
    // authenticated users
    PropertyStatus status = property.getStatus();
    if (status == PropertyStatus.DISPONIBLE
        || status == PropertyStatus.RESERVADO
        || status == PropertyStatus.EN_NEGOCIACION) {
      return;
    }

    throw new AccessDeniedException("You don't have permission to access these images");
  }

  private boolean isValidImageType(String mimeType, String fileName) {
    if (mimeType != null && ALLOWED_IMAGE_TYPES.contains(mimeType.toLowerCase())) {
      return true;
    }
    String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    return Set.of("jpg", "jpeg", "png", "webp", "heic", "heif").contains(ext);
  }

  private String buildImageObjectKey(String propertyId, String fileName, boolean isPending) {
    String timestamp = String.valueOf(Instant.now().toEpochMilli());
    String safeFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    String folder = isPending ? "pending/" : "";
    return String.format(
        "properties/%s/images/%s%s_%s", propertyId, folder, timestamp, safeFileName);
  }

  private String buildImageObjectKey(String propertyId, String fileName) {
    return buildImageObjectKey(propertyId, fileName, false);
  }

  private String getPublicUrl(String objectKey) {
    return String.format("http://localhost:9000/%s/%s", imagesBucket, objectKey);
  }

  private void ensureImagesBucketExists() {
    storageService.ensureBucketExists(imagesBucket);
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
    Object principal = auth.getPrincipal();
    if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
      return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
    }
    return getCurrentUserId();
  }

  private ImageResponse toImageResponse(ImageMetadata image, String temporaryUrl) {
    return ImageResponse.builder()
        .id(image.getId())
        .originalFileName(image.getOriginalFileName())
        .objectKey(image.getObjectKey())
        .publicUrl(image.getPublicUrl())
        .fileSize(image.getFileSize())
        .mimeType(image.getMimeType())
        .width(image.getWidth())
        .height(image.getHeight())
        .isPrimary(image.getIsPrimary())
        .displayOrder(image.getDisplayOrder())
        .uploadedAt(image.getUploadedAt())
        .uploadedBy(image.getUploadedBy())
        .uploadedByName(image.getUploadedByName())
        .status(image.getStatus())
        .accessPolicy(image.getAccessPolicy())
        .temporaryDownloadUrl(temporaryUrl)
        .expiresInSeconds(presignedExpiryMinutes * 60)
        .build();
  }
}

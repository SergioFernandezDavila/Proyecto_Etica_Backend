package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.dto.request.ConfirmImageUploadRequest;
import com.inmobiliaria.property_service.dto.request.GenerateImageUploadUrlRequest;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import com.inmobiliaria.property_service.dto.response.ImageResponse;
import com.inmobiliaria.property_service.dto.response.ImageUploadPolicyResponse;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
import com.inmobiliaria.property_service.service.ImageService;
import com.inmobiliaria.property_service.service.PropertyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/properties/{propertyId}/images") // Base path for images sub-resource
@RequiredArgsConstructor
public class ImageController {

  private final ImageService imageService;
  private final PropertyService propertyService;
  private final ResponseFactory responseFactory;

  /** Step 1 (Enhanced): Get Presigned POST Policy for secure uploading */
  @PostMapping("/upload-policy")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<ImageUploadPolicyResponse>> generateUploadPolicy(
      @PathVariable String propertyId, @Valid @RequestBody GenerateImageUploadUrlRequest request) {
    request.setPropertyId(propertyId);
    ImageUploadPolicyResponse data = imageService.generateUploadPolicy(request);
    return ResponseEntity.ok(responseFactory.success("Upload policy generated", data));
  }

  /** Step 1 (Legacy): Get Presigned URL for uploading */
  @PostMapping("/upload-url")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Map<String, String>>> generateUploadUrl(
      @PathVariable String propertyId, @Valid @RequestBody GenerateImageUploadUrlRequest request) {
    request.setPropertyId(propertyId);
    Map<String, String> data = imageService.generatePresignedUploadUrl(request);
    return ResponseEntity.ok(responseFactory.success("Upload URL generated", data));
  }

  /** Step 2: Confirm upload and attach metadata to Property */
  @PostMapping("/confirm")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> confirmUpload(
      @PathVariable String propertyId, @Valid @RequestBody ConfirmImageUploadRequest request) {

    log.info(
        "Confirming image for property: {}, objectKey: {}", propertyId, request.getObjectKey());

    var property =
        imageService.confirmImageUpload(
            propertyId,
            request.getObjectKey(),
            request.getOriginalFileName(),
            request.getFileSize(),
            request.getMimeType(),
            request.getIsPrimary() != null ? request.getIsPrimary() : false);

    PropertyResponse data = propertyService.mapToResponse(property);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseFactory.created("Image attached successfully", data));
  }

  /** List all images for a property */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<List<ImageResponse>>> getImages(
      @PathVariable String propertyId) {
    List<ImageResponse> data = imageService.getPropertyImages(propertyId);
    return ResponseEntity.ok(responseFactory.success("Images retrieved", data));
  }

  /** Set primary image */
  @PutMapping("/{imageId}/primary")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<ImageResponse>> setPrimaryImage(
      @PathVariable String propertyId, @PathVariable String imageId) {
    ImageResponse data = imageService.setPrimaryImage(propertyId, imageId);
    return ResponseEntity.ok(responseFactory.success("Primary image set", data));
  }

  /** Reorder images */
  @PostMapping("/reorder")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<ImageResponse>>> reorderImages(
      @PathVariable String propertyId, @RequestBody List<String> orderedImageIds) {
    List<ImageResponse> data = imageService.reorderImages(propertyId, orderedImageIds);
    return ResponseEntity.ok(responseFactory.success("Images reordered", data));
  }

  /** Delete image */
  @DeleteMapping("/{imageId}")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> deleteImage(
      @PathVariable String propertyId, @PathVariable String imageId) {
    imageService.deleteImage(propertyId, imageId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .body(responseFactory.deleted("Image deleted successfully"));
  }
}

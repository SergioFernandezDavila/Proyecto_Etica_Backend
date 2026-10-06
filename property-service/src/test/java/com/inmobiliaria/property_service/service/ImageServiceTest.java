package com.inmobiliaria.property_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.domain.PropertyStatus;
import com.inmobiliaria.property_service.dto.request.GenerateImageUploadUrlRequest;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImageService Unit Tests")
class ImageServiceTest {

  @Mock private StorageService storageService;
  @Mock private PropertyRepository propertyRepository;

  @InjectMocks private ImageService imageService;

  private PropertyDocument testProperty;
  private GenerateImageUploadUrlRequest uploadRequest;

  @BeforeEach
  @SuppressWarnings("unused")
  void setUp() {
    testProperty =
        PropertyDocument.builder()
            .id("property-123")
            .title("Test Property")
            .address("123 Test St")
            .zone("Downtown")
            .price(100000.0)
            .type("Apartment")
            .operationType(OperationType.VENTA)
            .m2(100.0)
            .rooms(3)
            .status(PropertyStatus.DISPONIBLE)
            .assignedAgentId("agent-123")
            .ownerId("owner-123")
            .deleted(false)
            .build();

    uploadRequest = new GenerateImageUploadUrlRequest();
    uploadRequest.setPropertyId("property-123");
    uploadRequest.setFileName("test-image.jpg");
    uploadRequest.setMimeType("image/jpeg");
    uploadRequest.setFileSize(1024000L);

    // Set security context
    SecurityContext securityContext = mock(SecurityContext.class);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn("agent-123");
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    // Set reflection values for presignedExpiryMinutes
    ReflectionTestUtils.setField(imageService, "presignedExpiryMinutes", 15);
    ReflectionTestUtils.setField(imageService, "imagesBucket", "property-images");
  }

  // Note: These tests primarily check the logic within ImageService methods, including validation
  // and interaction with the StorageService.
  // They do not cover the actual integration with MinIO or the full security context, which would
  // require more complex setup or integration tests.

  @Test
  @DisplayName("generatePresignedUploadUrl should return upload URL for valid request")
  void testGeneratePresignedUploadUrl() {
    // Arrange
    when(propertyRepository.findById("property-123")).thenReturn(Optional.of(testProperty));
    when(storageService.generatePresignedPutUrl(anyString(), anyString(), anyInt()))
        .thenReturn("https://minio.example.com/presigned-url");

    // Act
    var result = imageService.generatePresignedUploadUrl(uploadRequest);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey("uploadUrl"));
    assertTrue(result.containsKey("objectKey"));
    verify(storageService).generatePresignedPutUrl(anyString(), anyString(), anyInt());
  }

  @Test
  @DisplayName("generateUploadPolicy should generate upload policy")
  void testGenerateUploadPolicy() {
    // Arrange
    when(propertyRepository.findById("property-123")).thenReturn(Optional.of(testProperty));
    when(storageService.generateUploadPolicy(
            anyString(), anyString(), anyString(), anyLong(), anyInt()))
        .thenReturn(
            java.util.Map.of(
                "url", "https://minio.example.com", "formData", java.util.Map.of("key", "value")));

    // Act
    var result = imageService.generateUploadPolicy(uploadRequest);

    // Assert
    assertNotNull(result);
    assertEquals("https://minio.example.com", result.getUrl());
    verify(storageService)
        .generateUploadPolicy(anyString(), anyString(), anyString(), anyLong(), anyInt());
  }

  @Test
  @DisplayName("uploadImageDirectly should upload file successfully")
  void testUploadImageDirectly() {
    // Arrange
    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.jpg", "image/jpeg", "test content".getBytes());

    // Mock the void method
    doNothing()
        .when(storageService)
        .uploadObject(anyString(), anyString(), any(), anyLong(), anyString());

    // Act
    String result = imageService.uploadImageDirectly("property-123", file);

    // Assert
    assertNotNull(result);
    verify(storageService).uploadObject(anyString(), anyString(), any(), anyLong(), anyString());
  }
}

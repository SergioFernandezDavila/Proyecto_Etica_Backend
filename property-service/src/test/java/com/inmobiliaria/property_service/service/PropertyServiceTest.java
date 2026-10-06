package com.inmobiliaria.property_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.domain.PropertyStatus;
import com.inmobiliaria.property_service.dto.request.PropertyRequest;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PropertyService Unit Tests")
class PropertyServiceTest {

  @Mock private PropertyRepository propertyRepository;

  @InjectMocks private PropertyService propertyService;

  private PropertyDocument testProperty;
  private PropertyRequest testPropertyRequest;

  @BeforeEach
  @SuppressWarnings("unused")
  void setUp() {
    testProperty =
        PropertyDocument.builder()
            .id("test-property-id")
            .title("Test Property")
            .address("123 Test Street")
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

    testPropertyRequest =
        new PropertyRequest(
            "Test Property",
            "123 Test Street",
            "Downtown",
            100000.0,
            "Apartment",
            OperationType.VENTA,
            100.0,
            3,
            java.util.Set.of(),
            null);
  }

  // Note: These tests primarily check the logic within PropertyService methods, including
  // validation and interaction with the repository.
  // They do not cover the actual integration with MongoDB or the full security context, which would
  // require more complex setup or integration tests.

  @Test
  @DisplayName("findById should return property when it exists")
  void testFindByIdSuccess() {
    // Arrange
    when(propertyRepository.findById("test-property-id")).thenReturn(Optional.of(testProperty));

    // Act
    PropertyResponse result = propertyService.findById("test-property-id");

    // Assert
    assertNotNull(result);
    assertEquals("Test Property", result.title());
    verify(propertyRepository).findById("test-property-id");
  }

  @Test
  @DisplayName("findById should throw exception when property not found")
  void testFindByIdNotFound() {
    // Arrange
    when(propertyRepository.findById("invalid-id")).thenReturn(Optional.empty());

    // Act & Assert
    ResourceNotFoundException exception =
        assertThrows(ResourceNotFoundException.class, () -> propertyService.findById("invalid-id"));
    assertNotNull(exception);
    verify(propertyRepository).findById("invalid-id");
  }

  @Test
  @DisplayName("create should persist new property")
  void testCreatePropertySuccess() {
    // Arrange
    String agentId = "agent-123";
    when(propertyRepository.save(any(PropertyDocument.class))).thenReturn(testProperty);

    // Act
    PropertyResponse result = propertyService.create(testPropertyRequest, agentId);

    // Assert
    assertNotNull(result);
    assertEquals("Test Property", result.title());
    verify(propertyRepository).save(any(PropertyDocument.class));
  }

  @Test
  @DisplayName("findByAgent should return properties assigned to agent")
  void testFindByAgentSuccess() {
    // Arrange
    List<PropertyDocument> properties = Arrays.asList(testProperty);
    when(propertyRepository.findByAssignedAgentIdAndDeletedFalse("agent-123"))
        .thenReturn(properties);

    // Act
    List<PropertyResponse> result = propertyService.findByAgent("agent-123");

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    verify(propertyRepository).findByAssignedAgentIdAndDeletedFalse("agent-123");
  }

  @Test
  @DisplayName("findByOwner should return properties owned by user")
  void testFindByOwnerSuccess() {
    // Arrange
    List<PropertyDocument> properties = Arrays.asList(testProperty);
    when(propertyRepository.findByOwnerIdAndDeletedFalse("owner-123")).thenReturn(properties);

    // Act
    List<PropertyResponse> result = propertyService.findByOwner("owner-123");

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    verify(propertyRepository).findByOwnerIdAndDeletedFalse("owner-123");
  }

  @Test
  @DisplayName("updatePrice should change property price")
  void testUpdatePriceSuccess() {
    // Arrange
    Double newPrice = 150000.0;
    when(propertyRepository.findById("test-property-id")).thenReturn(Optional.of(testProperty));
    when(propertyRepository.save(any(PropertyDocument.class))).thenReturn(testProperty);

    // Act
    PropertyResponse result = propertyService.updatePrice("test-property-id", newPrice, "admin");

    // Assert
    assertNotNull(result);
    verify(propertyRepository).findById("test-property-id");
    verify(propertyRepository).save(any(PropertyDocument.class));
  }

  @Test
  @DisplayName("deleteProperty should mark property as deleted")
  void testDeletePropertySuccess() {
    // Arrange
    when(propertyRepository.findById("test-property-id")).thenReturn(Optional.of(testProperty));
    when(propertyRepository.save(any(PropertyDocument.class))).thenReturn(testProperty);

    // Act
    propertyService.deleteProperty("test-property-id", "admin");

    // Assert
    verify(propertyRepository).findById("test-property-id");
    verify(propertyRepository).save(any(PropertyDocument.class));
  }

  @Test
  @DisplayName("updateStatus should change property status")
  void testUpdateStatusSuccess() {
    // Arrange
    testProperty.setStatus(PropertyStatus.DISPONIBLE);
    when(propertyRepository.findById("test-property-id")).thenReturn(Optional.of(testProperty));
    when(propertyRepository.save(any(PropertyDocument.class))).thenReturn(testProperty);

    // Act
    PropertyResponse result =
        propertyService.updateStatus(
            "test-property-id", "RESERVADO", "admin", Arrays.asList("ROLE_ADMIN"), false);

    // Assert
    assertNotNull(result);
    verify(propertyRepository).save(any(PropertyDocument.class));
  }
}

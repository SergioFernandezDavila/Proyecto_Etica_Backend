package com.inmobiliaria.property_service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.domain.PropertyStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

@Disabled("Requires authenticated MongoDB - run with: docker-compose -f docker-compose.yml up -d")
@DataMongoTest
@ActiveProfiles("test")
@DisplayName("PropertyRepository Integration Tests")
class PropertyRepositoryTest {

  @Autowired private PropertyRepository propertyRepository;

  private PropertyDocument testProperty;

  @BeforeEach
  @SuppressWarnings("unused")
  void setUp() {
    // Clean up before each test - skip if MongoDB auth fails
    try {
      propertyRepository.deleteAll();
    } catch (Exception e) {
      // MongoDB may require authentication - skip cleanup if unavailable
    }

    testProperty =
        PropertyDocument.builder()
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
  }

  // Note: These tests interact with an actual MongoDB instance (embedded or test container).
  // They verify that the repository methods correctly persist and query data.

  @Test
  @DisplayName("save should persist property")
  void testSaveProperty() {
    // Act
    PropertyDocument saved = propertyRepository.save(testProperty);

    // Assert
    assertNotNull(saved.getId());
    assertEquals("Test Property", saved.getTitle());
    assertTrue(propertyRepository.existsById(saved.getId()));
  }

  @Test
  @DisplayName("findById should return property")
  void testFindById() {
    // Arrange
    PropertyDocument saved = propertyRepository.save(testProperty);

    // Act
    var result = propertyRepository.findById(saved.getId());

    // Assert
    assertTrue(result.isPresent());
    assertEquals("Test Property", result.get().getTitle());
  }

  @Test
  @DisplayName("findByDeletedFalse should return non-deleted properties")
  void testFindByDeletedFalse() {
    // Arrange
    propertyRepository.save(testProperty);
    PropertyDocument deletedProperty =
        PropertyDocument.builder()
            .title("Deleted Property")
            .address("456 Delete St")
            .zone("Oldtown")
            .price(50000.0)
            .type("House")
            .operationType(OperationType.VENTA)
            .m2(150.0)
            .rooms(5)
            .status(PropertyStatus.RETIRADO)
            .assignedAgentId("agent-456")
            .ownerId("owner-456")
            .deleted(true)
            .build();
    propertyRepository.save(deletedProperty);

    // Act
    List<PropertyDocument> result = propertyRepository.findByDeletedFalse();

    // Assert
    assertEquals(1, result.size());
    assertEquals("Test Property", result.get(0).getTitle());
  }

  @Test
  @DisplayName("findByAssignedAgentIdAndDeletedFalse should return agent's active properties")
  void testFindByAssignedAgentIdAndDeletedFalse() {
    // Arrange
    propertyRepository.save(testProperty);

    // Act
    List<PropertyDocument> result =
        propertyRepository.findByAssignedAgentIdAndDeletedFalse("agent-123");

    // Assert
    assertEquals(1, result.size());
    assertEquals("agent-123", result.get(0).getAssignedAgentId());
    assertFalse(result.get(0).isDeleted());
  }

  @Test
  @DisplayName("findByOwnerIdAndDeletedFalse should return owner's active properties")
  void testFindByOwnerIdAndDeletedFalse() {
    // Arrange
    propertyRepository.save(testProperty);

    // Act
    List<PropertyDocument> result = propertyRepository.findByOwnerIdAndDeletedFalse("owner-123");

    // Assert
    assertEquals(1, result.size());
    assertEquals("owner-123", result.get(0).getOwnerId());
    assertFalse(result.get(0).isDeleted());
  }

  @Test
  @DisplayName("findByAssignedAgentId should return all agent's properties")
  void testFindByAssignedAgentId() {
    // Arrange
    propertyRepository.save(testProperty);
    PropertyDocument deletedByAgent =
        PropertyDocument.builder()
            .title("Deleted by Agent")
            .address("789 Delete Ave")
            .zone("Oldtown")
            .price(75000.0)
            .type("Condo")
            .operationType(OperationType.ALQUILER)
            .m2(80.0)
            .rooms(2)
            .status(PropertyStatus.RETIRADO)
            .assignedAgentId("agent-123")
            .ownerId("owner-789")
            .deleted(true)
            .build();
    propertyRepository.save(deletedByAgent);

    // Act
    List<PropertyDocument> result = propertyRepository.findByAssignedAgentId("agent-123");

    // Assert
    assertEquals(2, result.size());
  }

  @Test
  @DisplayName("findByOwnerId should return all owner's properties")
  void testFindByOwnerId() {
    // Arrange
    propertyRepository.save(testProperty);

    // Act
    List<PropertyDocument> result = propertyRepository.findByOwnerId("owner-123");

    // Assert
    assertEquals(1, result.size());
    assertEquals("owner-123", result.get(0).getOwnerId());
  }

  @Test
  @DisplayName("delete should remove property")
  void testDeleteProperty() {
    // Arrange
    PropertyDocument saved = propertyRepository.save(testProperty);

    // Act
    propertyRepository.delete(saved);

    // Assert
    assertFalse(propertyRepository.existsById(saved.getId()));
  }
}

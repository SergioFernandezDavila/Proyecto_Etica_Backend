package com.inmobiliaria.operation_service.service;

import com.inmobiliaria.operation_service.client.PropertyClient;
import com.inmobiliaria.operation_service.domain.OperationDocument;
import com.inmobiliaria.operation_service.domain.OperationStatusHistory;
import com.inmobiliaria.operation_service.dto.OperationRequest;
import com.inmobiliaria.operation_service.dto.OperationResponse;
import com.inmobiliaria.operation_service.exception.ResourceNotFoundException;
import com.inmobiliaria.operation_service.exception.ValidationException;
import com.inmobiliaria.operation_service.repository.OperationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationService {

  private final OperationRepository operationRepository;
  private final PropertyClient propertyClient;

  public List<OperationResponse> findAll(String userId, String rolesHeader) {
    log.info(
        "[OperationService] Finding operations. User: {}, RolesHeader: {}", userId, rolesHeader);

    if (rolesHeader == null || rolesHeader.isBlank()) {
      log.warn("[OperationService] No roles provided for user: {}", userId);
      return new ArrayList<>();
    }

    // Clean and normalize roles: "[ROLE_ADMIN, AGENT]" -> ["ROLE_ADMIN", "AGENT"]
    String cleanRoles =
        rolesHeader.replace("[", "").replace("]", "").replace(" ", "").toUpperCase();
    List<String> roles = Arrays.asList(cleanRoles.split(","));
    log.debug("[OperationService] Normalized roles: {}", roles);

    // Check for Admin (flexible check)
    if (roles.contains("ROLE_ADMIN") || roles.contains("ADMIN")) {
      log.info("[OperationService] User {} has ADMIN privileges. Fetching all operations.", userId);
      return latestOperationPerProperty(operationRepository.findAll()).stream()
          .map(this::mapToResponse)
          .collect(Collectors.toList());
    }

    Set<OperationDocument> combinedOperations = new HashSet<>();

    // Check for Agent
    if (roles.contains("ROLE_AGENT") || roles.contains("AGENT")) {
      List<OperationDocument> agentOps = operationRepository.findByAgentId(userId);
      log.debug("[OperationService] Found {} operations for agent: {}", agentOps.size(), userId);
      combinedOperations.addAll(agentOps);
    }

    // Owner and Client check (based on ID match in document)
    List<OperationDocument> ownerOps = operationRepository.findByOwnerId(userId);
    List<OperationDocument> clientOps = operationRepository.findByClientId(userId);

    log.debug(
        "[OperationService] ID matches -> Owner: {}, Client: {}",
        ownerOps.size(),
        clientOps.size());

    combinedOperations.addAll(ownerOps);
    combinedOperations.addAll(clientOps);

    log.info(
        "[OperationService] Total unique operations for user {}: {}",
        userId,
        combinedOperations.size());

    return latestOperationPerProperty(combinedOperations.stream().toList()).stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  public OperationResponse findById(String id) {
    OperationDocument operation =
        operationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Operation not found with id: " + id));
    return mapToResponse(operation);
  }

  public OperationResponse findByPropertyId(String propertyId) {
    OperationDocument operation =
        operationRepository
            .findFirstByPropertyIdOrderByCreatedAtDesc(propertyId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No operation found for propertyId: " + propertyId));
    return mapToResponse(operation);
  }

  public OperationResponse create(OperationRequest request, String userId, String roles) {
    return create(request, userId, roles, true);
  }

  public OperationResponse create(
      OperationRequest request, String userId, String roles, boolean syncProperty) {
    // Prevent duplicate closure
    if ("CLOSED".equalsIgnoreCase(request.getStatus())) {
      operationRepository
          .findByPropertyIdAndStatus(request.getPropertyId(), "CLOSED")
          .ifPresent(
              op -> {
                throw new com.inmobiliaria.operation_service.exception.ValidationException(
                    "This property already has a CLOSED operation record.");
              });
    }

    OperationDocument operation =
        OperationDocument.builder()
            .propertyId(request.getPropertyId())
            .propertyName(request.getPropertyName())
            .propertyType(request.getPropertyType())
            .operationType(request.getOperationType())
            .finalPrice(request.getFinalPrice())
            .currency(request.getCurrency())
            .clientId(request.getClientId())
            .clientName(request.getClientName())
            .agentId(request.getAgentId())
            .agentName(request.getAgentName())
            .ownerId(request.getOwnerId())
            .ownerName(request.getOwnerName())
            .department(request.getDepartment())
            .status(request.getStatus())
            .notes(request.getNotes())
            .closureDate(
                request.getClosureDate() != null ? request.getClosureDate() : LocalDateTime.now())
            .build();

    operation.setCreatedAt(Instant.now());
    operation.setCreatedBy(userId);
    appendStatusHistory(operation, null, request.getStatus(), userId, "Operation created");

    OperationDocument saved = operationRepository.save(operation);

    if (syncProperty) {
      try {
        syncPropertyStatus(saved.getPropertyId(), saved.getStatus(), userId);
      } catch (Exception e) {
        log.error("Failed to sync status, rolling back operation creation: {}", e.getMessage());
        operationRepository.delete(saved);
        throw new com.inmobiliaria.operation_service.exception.ValidationException(
            "Failed to update property status. Operation cancelled: " + e.getMessage());
      }
    }

    return mapToResponse(saved);
  }

  public OperationResponse updateStatus(
      String id, String status, String userId, String rolesHeader) {
    // Buscar la operación una sola vez
    OperationDocument operation =
        operationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Operation not found with id: " + id));

    String oldStatus = operation.getStatus();

    // Normalizar roles - agregar import org.springframework.http.HttpStatus si es necesario
    if (rolesHeader == null) rolesHeader = "";
    String cleanRoles =
        rolesHeader.replace("[", "").replace("]", "").replace(" ", "").toUpperCase();
    List<String> rawRoles = Arrays.asList(cleanRoles.split(","));

    // Normalizar roles (asegurar que tengan prefijo ROLE_)
    List<String> normalizedRoles =
        rawRoles.stream()
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .collect(Collectors.toList());

    boolean isAdmin = normalizedRoles.contains("ROLE_ADMIN");
    boolean isAssignedAgent = userId.equals(operation.getAgentId());

    if (!isAdmin && !isAssignedAgent) {
      throw new ValidationException(
          "Only Admins or the assigned Agent can update the operation status.");
    }

    // Validaciones de estado
    if ("CLOSED".equalsIgnoreCase(oldStatus) && !"CANCELLED".equalsIgnoreCase(status)) {
      throw new ValidationException(
          "Esta operación ya ha sido CERRADA de forma definitiva y no puede ser modificada.");
    }

    if ("CANCELLED".equalsIgnoreCase(oldStatus)) {
      throw new ValidationException("La operación ya ha sido cancelada y no puede ser reactivada.");
    }

    // Actualizar estado
    operation.setStatus(status);
    operation.setUpdatedAt(Instant.now());
    appendStatusHistory(operation, oldStatus, status, userId, "Operation status updated");
    OperationDocument saved = operationRepository.save(operation);

    try {
      syncPropertyStatus(saved.getPropertyId(), status, userId);
    } catch (Exception e) {
      log.error("Failed to sync status, rolling back status update: {}", e.getMessage());
      operation.setStatus(oldStatus);
      removeLatestStatusHistory(operation);
      operationRepository.save(operation);
      throw new ValidationException(
          "Failed to update property status. Reverting operation status: " + e.getMessage());
    }

    return mapToResponse(saved);
  }

  public OperationResponse cancelSoldOperationForProperty(
      String propertyId, String userId, String rolesHeader) {
    OperationDocument soldOperation =
        operationRepository
            .findFirstByPropertyIdAndStatusOrderByCreatedAtDesc(propertyId, "CLOSED")
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No CLOSED operation found for propertyId: " + propertyId));

    return updateStatus(soldOperation.getId(), "CANCELLED", userId, rolesHeader);
  }

  private void syncPropertyStatus(String propertyId, String operationStatus, String userId) {
    String propertyStatus = mapOperationToPropertyStatus(operationStatus);
    if (propertyStatus != null) {
      propertyClient.updateStatus(
          propertyId, new PropertyClient.UpdateStatusRequest(propertyStatus), userId, true);
      log.info(
          "Synced property {} status to {} due to operation status {}",
          propertyId,
          propertyStatus,
          operationStatus);
    }
  }

  private String mapOperationToPropertyStatus(String operationStatus) {
    return switch (operationStatus.toUpperCase()) {
      case "CLOSED" -> "VENDIDO";
      case "PENDING" -> "RESERVADO";
      case "ACTIVE" -> "EN_NEGOCIACION";
      case "CANCELLED" -> null;
      default -> null;
    };
  }

  private List<OperationDocument> latestOperationPerProperty(List<OperationDocument> operations) {
    Map<String, OperationDocument> latestByProperty = new LinkedHashMap<>();

    operations.stream()
        .sorted(this::compareNewestFirst)
        .forEach(
            operation -> {
              String key =
                  operation.getPropertyId() != null ? operation.getPropertyId() : operation.getId();
              latestByProperty.putIfAbsent(key, operation);
            });

    return new ArrayList<>(latestByProperty.values());
  }

  private int compareNewestFirst(OperationDocument a, OperationDocument b) {
    Instant aDate = a.getCreatedAt();
    Instant bDate = b.getCreatedAt();
    if (aDate == null && bDate == null) return 0;
    if (aDate == null) return 1;
    if (bDate == null) return -1;
    return bDate.compareTo(aDate);
  }

  private void appendStatusHistory(
      OperationDocument operation,
      String oldStatus,
      String newStatus,
      String userId,
      String notes) {
    if (operation.getStatusHistory() == null) {
      operation.setStatusHistory(new ArrayList<>());
    }
    operation
        .getStatusHistory()
        .add(
            OperationStatusHistory.builder()
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedAt(Instant.now())
                .changedBy(userId)
                .notes(notes)
                .build());
  }

  private void removeLatestStatusHistory(OperationDocument operation) {
    if (operation.getStatusHistory() == null || operation.getStatusHistory().isEmpty()) {
      return;
    }
    operation.getStatusHistory().remove(operation.getStatusHistory().size() - 1);
  }

  private OperationResponse mapToResponse(OperationDocument op) {
    return OperationResponse.builder()
        .id(op.getId())
        .propertyId(op.getPropertyId())
        .propertyName(op.getPropertyName())
        .propertyType(op.getPropertyType())
        .operationType(op.getOperationType())
        .finalPrice(op.getFinalPrice())
        .currency(op.getCurrency())
        .clientId(op.getClientId())
        .clientName(op.getClientName())
        .agentId(op.getAgentId())
        .agentName(op.getAgentName())
        .ownerId(op.getOwnerId())
        .ownerName(op.getOwnerName())
        .department(op.getDepartment())
        .status(op.getStatus())
        .notes(op.getNotes())
        .closureDate(op.getClosureDate())
        .createdAt(
            op.getCreatedAt() != null
                ? LocalDateTime.ofInstant(op.getCreatedAt(), java.time.ZoneOffset.UTC)
                : null)
        .statusHistory(op.getStatusHistory() != null ? op.getStatusHistory() : new ArrayList<>())
        .build();
  }
}

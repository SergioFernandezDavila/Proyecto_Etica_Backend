package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.client.IdentityClient;
import com.inmobiliaria.property_service.client.NotificationClient;
import com.inmobiliaria.property_service.client.OperationClient;
import com.inmobiliaria.property_service.client.UserClient;
import com.inmobiliaria.property_service.domain.*;
import com.inmobiliaria.property_service.dto.request.*;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.dto.response.ResponsableResponse;
import com.inmobiliaria.property_service.exception.AccessDeniedException;
import com.inmobiliaria.property_service.exception.ConflictException;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.exception.ValidationException;
import com.inmobiliaria.property_service.repository.AuditLogRepository;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import com.inmobiliaria.property_service.security.Auditable;
import com.inmobiliaria.property_service.util.QRCodeGenerator;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

  private final PropertyRepository propertyRepository;
  private final IdentityClient identityClient;
  private final OperationClient operationClient;
  private final MongoTemplate mongoTemplate;
  private final ImageService imageService;
  private final UserClient userClient;
  private final PdfGenerationService pdfGenerationService;
  private final NotificationClient notificationClient;
  private final AuditLogRepository auditLogRepository;
  private final MinioClient minioClient;

  /** Búsqueda avanzada con filtros dinámicos y seguridad por rol. */
  public Map<String, Object> findWithFilters(
      String title,
      String type,
      String zone,
      String status,
      OperationType operationType,
      Double minPrice,
      Double maxPrice,
      Double minM2,
      Double maxM2,
      String agentId,
      String currentUserId,
      List<String> roles,
      String sortBy,
      String sortOrder,
      int page,
      int pageSize) {

    Query query = new Query();
    List<Criteria> filters = new ArrayList<>();

    // 1. Manejo de borrado lógico (ELIMINADO)
    if (status != null && status.equalsIgnoreCase("ELIMINADO")) {
      filters.add(Criteria.where("deleted").is(true));
    } else {
      filters.add(Criteria.where("deleted").is(false));
    }

    // 2. Filtro de estado específico
    if (status != null && !status.isBlank()) {
      try {
        filters.add(Criteria.where("status").is(PropertyStatus.valueOf(status.toUpperCase())));
      } catch (IllegalArgumentException e) {
        log.warn("Invalid status requested: {}", status);
      }
    } else {
      // DEFAULT: Hide VENDIDO properties from main listings unless explicitly requested
      // ELIMINADO is already handled by the 'deleted' flag filter above
      filters.add(Criteria.where("status").ne(PropertyStatus.VENDIDO));

      if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_AGENT")) {
        // FOR CLIENTS: Only show relevant states
        filters.add(
            Criteria.where("status")
                .in(
                    PropertyStatus.DISPONIBLE,
                    PropertyStatus.RESERVADO,
                    PropertyStatus.EN_NEGOCIACION));
      }
    }

    if (title != null && !title.isBlank()) {
      filters.add(Criteria.where("title").regex(title, "i"));
    }
    if (type != null && !type.isBlank()) {
      filters.add(Criteria.where("type").is(type));
    }
    if (zone != null && !zone.isBlank()) {
      filters.add(Criteria.where("zone").is(zone));
    }
    if (operationType != null) {
      filters.add(Criteria.where("operationType").is(operationType));
    }
    if (minPrice != null) {
      filters.add(Criteria.where("price").gte(minPrice));
    }
    if (maxPrice != null) {
      filters.add(Criteria.where("price").lte(maxPrice));
    }
    if (minM2 != null) {
      filters.add(Criteria.where("m2").gte(minM2));
    }
    if (maxM2 != null) {
      filters.add(Criteria.where("m2").lte(maxM2));
    }
    if (agentId != null && !agentId.isBlank()) {
      filters.add(Criteria.where("assignedAgentId").is(agentId));
    }

    // Seguridad: Si no es ADMIN, solo ve sus asignadas, las permitidas por política (PUBLIC por
    // defecto) o cualquier propiedad disponible/en curso (HU: Visibilidad pública)
    if (!roles.contains("ROLE_ADMIN")) {
      Criteria securityCriteria =
          new Criteria()
              .orOperator(
                  Criteria.where("assignedAgentId").is(currentUserId),
                  Criteria.where("ownerId").is(currentUserId),
                  Criteria.where("accessPolicy")
                      .in(
                          currentUserId,
                          "ROLE_AGENT",
                          "PUBLIC",
                          "ROLE_USER",
                          "rol_interested_client"),
                  Criteria.where("status")
                      .in(
                          PropertyStatus.DISPONIBLE,
                          PropertyStatus.RESERVADO,
                          PropertyStatus.EN_NEGOCIACION));
      filters.add(securityCriteria);
    }

    if (!filters.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
    }

    Sort.Direction direction =
        "DESC".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
    String sortField =
        switch (sortBy) {
          case "title" -> "title";
          case "m2" -> "m2";
          case "rooms" -> "rooms";
          case "status" -> "status";
          default -> "price";
        };

    long total = mongoTemplate.count(query, PropertyDocument.class);

    query.with(Sort.by(direction, sortField)).skip((long) page * pageSize).limit(pageSize);

    List<PropertyResponse> data =
        mongoTemplate.find(query, PropertyDocument.class).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("data", data);
    result.put("page", page);
    result.put("pageSize", pageSize);
    result.put("totalElements", total);
    result.put("totalPages", (int) Math.ceil((double) total / pageSize));
    return result;
  }

  @Auditable(action = "PROPERTY_UPDATE") // Los agentes también auditan su update
  public PropertyResponse updatePropertyAsAgent(
      String id, AgentPropertyUpdateRequest request, String agentId) {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + id));

    // Verify the agent is assigned to this property
    boolean isAssignedAgent =
        property.getAssignedAgentId() != null && property.getAssignedAgentId().equals(agentId);

    if (!isAssignedAgent) {
      throw new AccessDeniedException("You can only update properties assigned to you");
    }

    // Update allowed fields (price is NOT included)
    property.setTitle(request.title());
    property.setAddress(request.address());
    property.setZone(request.zone());
    property.setType(request.type());
    property.setM2(request.m2());
    property.setRooms(request.rooms());
    property.setOperationType(request.operationType());

    if (request.ownerId() != null && !request.ownerId().isBlank()) {
      property.setOwnerId(request.ownerId());
    }

    property.setUpdatedAt(Instant.now());
    property.setCreatedBy(agentId);

    propertyRepository.save(property);

    log.info("Property {} updated by agent {}", id, agentId);

    return mapToResponse(propertyRepository.findById(id).orElseThrow());
  }

  public List<PropertyResponse> findAll() {
    return propertyRepository.findByDeletedFalse().stream().map(this::mapToResponse).toList();
  }

  public PropertyResponse findById(String id) {
    return propertyRepository
        .findById(id)
        .map(this::mapToResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado: " + id));
  }

  public ResponsableResponse getResponsable(String propertyId) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Inmueble no encontrado: " + propertyId));

    String agentId = property.getAssignedAgentId();
    if (agentId == null || agentId.isBlank()) {
      throw new ResourceNotFoundException("Este inmueble no tiene agente responsable asignado");
    }

    try {
      IdentityClient.UserResponse agent = identityClient.findById(agentId);
      if (agent == null) {
        throw new ResourceNotFoundException("No se pudo obtener información del responsable");
      }
      return new ResponsableResponse(
          agent.id(),
          agent.fullName() != null
              ? agent.fullName()
              : (agent.firstName() + " " + agent.lastName()).trim(),
          agent.email(),
          agent.phone());
    } catch (Exception e) {
      log.warn("No se pudo obtener datos del responsable {}: {}", agentId, e.getMessage());
      throw new ResourceNotFoundException("No se pudo obtener información del responsable");
    }
  }

  @Auditable(action = "PROPERTY_CREATE")
  public PropertyResponse create(PropertyRequest request, String agentId) {
    PropertyDocument property =
        PropertyDocument.builder()
            .title(request.title())
            .address(request.address())
            .zone(request.zone())
            .price(request.price())
            .type(request.type())
            .operationType(request.operationType())
            .m2(request.m2())
            .rooms(request.rooms())
            .status(PropertyStatus.DISPONIBLE)
            .assignedAgentId(agentId)
            .ownerId(request.ownerId())
            .accessPolicy(
                request.accessPolicy() != null && !request.accessPolicy().isEmpty()
                    ? request.accessPolicy()
                    : new HashSet<>(List.of("PUBLIC")))
            .build();
    property.setCreatedAt(Instant.now());
    property.setCreatedBy(agentId);
    return mapToResponse(propertyRepository.save(property));
  }

  @Auditable(action = "PRICE_UPDATE")
  public PropertyResponse updatePrice(String id, Double newPrice, String adminId) {
    PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
    prop.getPriceHistory().add(new PriceHistory(prop.getPrice(), newPrice, Instant.now(), adminId));
    prop.setPrice(newPrice);
    return mapToResponse(propertyRepository.save(prop));
  }

  @Auditable(action = "AGENT_ASSIGN")
  public PropertyResponse assignAgent(String id, AssignAgentRequest request, String adminId) {
    PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
    var agent = identityClient.findById(request.agentId());
    if (agent == null || !"ACTIVE".equals(agent.status()))
      throw new RuntimeException("Agente inactivo");
    prop.getAssignmentHistory()
        .add(new AssignmentHistory(prop.getAssignedAgentId(), Instant.now(), adminId));
    prop.setAssignedAgentId(request.agentId());

    // --- NUEVO: Asignar el agente al dueño de la propiedad ---
    if (prop.getOwnerId() != null) {
      try {
        identityClient.updateUser(
            prop.getOwnerId(),
            new IdentityClient.UpdateUserRequest(
                null, null, null, null, null, null, null, null, null, null, request.agentId()));
        log.info("Agent {} assigned to owner {}", request.agentId(), prop.getOwnerId());
      } catch (Exception e) {
        log.warn("Failed to assign agent to owner {}: {}", prop.getOwnerId(), e.getMessage());
      }
    }

    return mapToResponse(propertyRepository.save(prop));
  }

  public List<PropertyResponse> findByAgent(String agentId) {
    return propertyRepository.findByAssignedAgentIdAndDeletedFalse(agentId).stream()
        .map(this::mapToResponse)
        .toList();
  }

  public List<PropertyResponse> findByOwner(String ownerId) {
    return propertyRepository.findByOwnerIdAndDeletedFalse(ownerId).stream()
        .map(this::mapToResponse)
        .toList();
  }

  @Auditable(action = "OWNER_ASSIGN")
  public PropertyResponse assignOwner(String id, String ownerId, String adminId) {
    PropertyDocument prop =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + id));

    // Optional: Validate owner exists in identity service
    try {
      var owner = identityClient.findById(ownerId);
      if (owner == null || !"ACTIVE".equals(owner.status())) {
        throw new ValidationException("Owner is not active");
      }
    } catch (Exception e) {
      log.warn("Could not validate owner: {}", e.getMessage());
    }

    prop.setOwnerId(ownerId);
    prop.setUpdatedAt(Instant.now());

    // Add to audit/assignment history if needed
    if (prop.getAssignmentHistory() == null) {
      prop.setAssignmentHistory(new ArrayList<>());
    }
    prop.getAssignmentHistory()
        .add(new AssignmentHistory(prop.getAssignedAgentId(), Instant.now(), adminId));

    return mapToResponse(propertyRepository.save(prop));
  }

  @Auditable(action = "LOCATION_UPDATE")
  public PropertyResponse updateLocation(
      String id, UpdateLocationRequest request, String userId, List<String> roles) {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + id));

    // Validar permisos (Admin o Agente asignado)
    boolean isAdmin = roles.contains("ROLE_ADMIN");
    boolean isAssignedAgent = userId.equals(property.getAssignedAgentId());

    if (!isAdmin && !isAssignedAgent) {
      throw new AccessDeniedException(
          "No tiene permisos para actualizar la ubicación de este inmueble");
    }

    property.setLatitude(request.latitude());
    property.setLongitude(request.longitude());
    property.setUpdatedAt(Instant.now());

    return mapToResponse(propertyRepository.save(property));
  }

  public PropertyResponse updateOperationType(String id, OperationType newType) {
    PropertyDocument prop =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));
    prop.setOperationType(newType);
    prop.setUpdatedAt(Instant.now());
    return mapToResponse(propertyRepository.save(prop));
  }

  /**
   * Mapea el documento a DTO incluyendo la generación de URLs temporales firmadas para imágenes.
   */
  public PropertyResponse mapToResponse(PropertyDocument doc) {
    List<String> urls = new ArrayList<>();

    if (doc.getImages() != null && !doc.getImages().isEmpty()) {
      urls =
          doc.getImages().stream()
              .sorted(
                  (a, b) -> {
                    if (Boolean.TRUE.equals(a.getIsPrimary())
                        && !Boolean.TRUE.equals(b.getIsPrimary())) return -1;
                    if (!Boolean.TRUE.equals(a.getIsPrimary())
                        && Boolean.TRUE.equals(b.getIsPrimary())) return 1;
                    int orderA = a.getDisplayOrder() != null ? a.getDisplayOrder() : 999;
                    int orderB = b.getDisplayOrder() != null ? b.getDisplayOrder() : 999;
                    return Integer.compare(orderA, orderB);
                  })
              .map(img -> imageService.generateTemporaryImageUrl(img))
              .collect(Collectors.toList());
    } else if (doc.getImageUrls() != null) {
      urls = doc.getImageUrls();
    }

    String agentName = "No asignado";
    if (doc.getAssignedAgentId() != null && !doc.getAssignedAgentId().isBlank()) {
      try {
        IdentityClient.UserResponse agent = identityClient.findById(doc.getAssignedAgentId());
        if (agent != null) {
          agentName = agent.fullName();
        }
      } catch (Exception e) {
        log.warn(
            "Could not fetch agent name for ID: {}. Error: {}",
            doc.getAssignedAgentId(),
            e.getMessage());
        agentName = "Agente (" + doc.getAssignedAgentId() + ")";
      }
    }

    return new PropertyResponse(
        doc.getId(),
        doc.getTitle(),
        doc.getAddress(),
        doc.getZone() != null ? doc.getZone() : "No especificada",
        doc.getPrice(),
        doc.getType(),
        doc.getOperationType(),
        doc.getM2(),
        doc.getRooms(),
        doc.getStatus() != null ? doc.getStatus().name() : null,
        doc.getAssignedAgentId(),
        agentName,
        doc.getOwnerId(),
        urls,
        doc.getAssignmentHistory() != null ? doc.getAssignmentHistory() : new ArrayList<>(),
        doc.getPriceHistory() != null ? doc.getPriceHistory() : new ArrayList<>(),
        doc.getStatusHistory() != null ? doc.getStatusHistory() : new ArrayList<>(),
        doc.getAccessPolicy() != null ? doc.getAccessPolicy() : new HashSet<>(),
        doc.getLatitude(),
        doc.getLongitude(),
        doc.getMotivoRetiro(),
        doc.getDetalleRetiro());
  }

  /**
   * CORRECCIÓN CRÍTICA: Eliminación de imagen estrictamente por ID. Esto evita que el API Gateway
   * detecte caracteres maliciosos (//) en la URL de la petición.
   */
  public PropertyResponse deleteImage(String propertyId, String imageId) {
    PropertyDocument property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

    if (property.getImages() != null) {
      // Buscamos estrictamente por el ID interno generado en MongoDB (UUID)
      Optional<ImageMetadata> imgOpt =
          property.getImages().stream().filter(img -> img.getId().equals(imageId)).findFirst();

      if (imgOpt.isPresent()) {
        log.info("Eliminando imagen con ID: {} de la propiedad: {}", imageId, propertyId);
        imageService.deleteImage(propertyId, imageId);
      } else {
        log.error(
            "No se pudo borrar: la imagen con ID {} no pertenece a la propiedad {}",
            imageId,
            propertyId);
        throw new ResourceNotFoundException("La imagen solicitada no existe en este inmueble");
      }
    }

    return mapToResponse(propertyRepository.findById(propertyId).orElseThrow());
  }

  @Auditable(action = "PROPERTY_DELETE")
  public void deleteProperty(String id, String adminId) {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado: " + id));

    if (property.getStatus() == PropertyStatus.VENDIDO) {
      throw new ValidationException(
          "No se puede eliminar un inmueble que ya ha sido VENDIDO por integridad de datos.");
    }

    property.setDeleted(true);
    property.setStatus(PropertyStatus.ELIMINADO);
    property.setUpdatedAt(Instant.now());

    propertyRepository.save(property);

    log.info("Propiedad {} marcada como eliminada (lógico) por admin: {}", id, adminId);
  }

  // ========== RETIRAR FEATURE (with reason tracking - from Sprint3_DEV) ==========
  @Auditable(action = "PROPERTY_RETIRE")
  public PropertyResponse retireProperty(
      String id, RetirePropertyRequest request, String userId, List<String> roles) {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + id));

    boolean isAdmin = roles.contains("ROLE_ADMIN");
    boolean isAssignedAgent =
        property.getAssignedAgentId() != null && property.getAssignedAgentId().equals(userId);
    if (!isAdmin && !isAssignedAgent) {
      throw new AccessDeniedException("No tiene permisos para retirar este inmueble.");
    }

    // Validación de estado previo
    if (property.getStatus() == PropertyStatus.VENDIDO
        || property.getStatus() == PropertyStatus.ELIMINADO) {
      throw new ValidationException(
          "No se puede retirar un inmueble que ya está VENDIDO o ELIMINADO.");
    }
    if (property.getStatus() == PropertyStatus.RETIRADO) {
      throw new ValidationException("El inmueble ya se encuentra retirado.");
    }

    // Validate reason details
    if (request.getMotivoRetiro() == RetirementReason.OTRO) {
      if (request.getDetalleRetiro() == null || request.getDetalleRetiro().isBlank()) {
        throw new ValidationException("Debe proporcionar un detalle cuando el motivo es 'Otro'.");
      }
    }

    // Registrar historial de estado
    if (property.getStatusHistory() == null) property.setStatusHistory(new ArrayList<>());
    property
        .getStatusHistory()
        .add(
            StatusHistory.builder()
                .oldStatus(property.getStatus().name())
                .newStatus(PropertyStatus.RETIRADO.name())
                .changedAt(Instant.now())
                .changedBy(userId)
                .build());

    // Actualizar campos
    property.setStatus(PropertyStatus.RETIRADO);
    property.setMotivoRetiro(request.getMotivoRetiro());
    property.setDetalleRetiro(request.getDetalleRetiro());
    property.setUpdatedAt(Instant.now());

    log.info("Property {} retired by {} with reason: {}", id, userId, request.getMotivoRetiro());
    return mapToResponse(propertyRepository.save(property));
  }

  // ========== STATUS CHANGE (merged from both) ==========
  @Auditable(action = "STATUS_CHANGE")
  public PropertyResponse updateStatus(
      String id, String newStatus, String currentUserId, List<String> roles, boolean isInternal) {
    PropertyDocument prop =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

    PropertyStatus oldStatus = prop.getStatus();
    PropertyStatus targetStatus = PropertyStatus.valueOf(newStatus.toUpperCase());

    // Delegate to retireProperty if target is RETIRADO
    if (targetStatus == PropertyStatus.RETIRADO) {
      throw new ValidationException(
          "No se puede cambiar el estado a RETIRADO a través de este endpoint. Use el endpoint específico /retirar con motivo.");
    }

    boolean isAdmin = roles.contains("ROLE_ADMIN");
    boolean isAssignedAgent = currentUserId.equals(prop.getAssignedAgentId());

    // --- Restriction: Business states must be driven by Operations ---
    if (!isInternal && !isAdmin) {
      if (targetStatus == PropertyStatus.VENDIDO
          || targetStatus == PropertyStatus.RESERVADO
          || targetStatus == PropertyStatus.EN_NEGOCIACION) {
        throw new ValidationException(
            "El estado "
                + targetStatus
                + " solo puede ser asignado a través del módulo de Operaciones.");
      }
    }

    // --- PA3: Validación de permisos y transiciones críticas ---
    if (!isAdmin && !isAssignedAgent && !isInternal) {
      throw new AccessDeniedException("No autorizado para modificar esta propiedad.");
    }

    // Bloquear cualquier reversión de un estado "VENDIDO" (Terminal y definitivo)
    if (oldStatus == PropertyStatus.VENDIDO) {
      throw new ValidationException(
          "Esta propiedad ha sido marcada como VENDIDA de forma definitiva. No es posible revertir su estado.");
    }

    // TRIGGER: Si se cambia a VENDIDO manualmente (por admin), crear registro de Operación
    if (targetStatus == PropertyStatus.VENDIDO && !isInternal) {
      triggerManualClosureOperation(prop, currentUserId, roles);
    }

    // --- Registro de Historial (PA:2) ---
    if (prop.getStatusHistory() == null) {
      prop.setStatusHistory(new ArrayList<>());
    }

    prop.getStatusHistory()
        .add(
            StatusHistory.builder()
                .oldStatus(oldStatus.name())
                .newStatus(targetStatus.name())
                .changedAt(Instant.now())
                .changedBy(currentUserId)
                .build());

    prop.setStatus(targetStatus);
    prop.setUpdatedAt(Instant.now());

    return mapToResponse(propertyRepository.save(prop));
  }

  private void triggerManualClosureOperation(
      PropertyDocument prop, String currentUserId, List<String> roles) {
    try {
      String ownerName = "Desconocido";
      if (prop.getOwnerId() != null) {
        try {
          var res = identityClient.findById(prop.getOwnerId());
          ownerName = res.fullName();
        } catch (Exception e) {
          log.warn("Could not fetch owner name for manual closure: {}", e.getMessage());
        }
      }

      String agentName = "Administrador";
      String dept = "Gerencia";
      try {
        var res = identityClient.findById(currentUserId);
        agentName = res.fullName();
        dept = res.department();
      } catch (Exception e) {
        log.warn("Could not fetch agent name for manual closure: {}", e.getMessage());
      }

      var opRequest =
          com.inmobiliaria.property_service.client.OperationClient.CreateOperationRequest.builder()
              .propertyId(prop.getId())
              .propertyName(prop.getTitle())
              .propertyType(prop.getType())
              .operationType(prop.getOperationType().name())
              .finalPrice(prop.getPrice())
              .currency("USD")
              .clientId("ADMIN_MANUAL")
              .clientName("Cierre Directo por Admin")
              .agentId(currentUserId)
              .agentName(agentName)
              .department(dept)
              .ownerId(prop.getOwnerId())
              .ownerName(ownerName)
              .status("CLOSED")
              .closureDate(java.time.LocalDateTime.now())
              .notes("Operación generada automáticamente por cambio de estado manual a VENDIDO.")
              .build();

      operationClient.createOperation(opRequest, currentUserId, String.join(",", roles), true);
      log.info("Manual closure operation triggered for property {}", prop.getId());
    } catch (Exception e) {
      log.error("Failed to trigger manual closure operation: {}", e.getMessage());
      throw new ValidationException(
          "No se pudo registrar la operación de cierre obligatoria: " + e.getMessage());
    }
  }

  @Auditable(action = "PROPERTY_UPDATE")
  public PropertyResponse updateProperty(String id, PropertyRequest request, String adminId) {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + id));

    Double currentPrice = property.getPrice();

    property.setTitle(request.title());
    property.setAddress(request.address());
    property.setZone(request.zone());
    property.setType(request.type());
    property.setM2(request.m2());
    property.setRooms(request.rooms());
    property.setOperationType(request.operationType());

    if (request.ownerId() != null && !request.ownerId().isBlank()) {
      property.setOwnerId(request.ownerId());
    }

    property.setUpdatedAt(Instant.now());
    property.setCreatedBy(adminId);

    propertyRepository.save(property);

    if (request.price() != null && !request.price().equals(currentPrice)) {
      updatePrice(id, request.price(), adminId);
      log.info(
          "Price updated for property {} from {} to {} by admin {}",
          id,
          currentPrice,
          request.price(),
          adminId);
    }

    log.info("Property {} updated by admin {}", id, adminId);

    return mapToResponse(propertyRepository.findById(id).orElseThrow());
  }

  public void validateAvailabilityForAction(String id) {
    PropertyDocument prop =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

    if (prop.getStatus() == PropertyStatus.VENDIDO
        || prop.getStatus() == PropertyStatus.RESERVADO) {
      throw new ValidationException(
          "El sistema no permite realizar esta acción: La propiedad ya está " + prop.getStatus());
    }
  }

  // ========== REINCORPORATE FEATURE (US-59 - relist property) ==========
  @Auditable(action = "PROPERTY_REINCORPORATE")
  public PropertyResponse reincorporateProperty(String id, String userId, List<String> roles) {
    PropertyDocument prop =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

    PropertyStatus currentStatus = prop.getStatus();

    // Validación: Solo se pueden reincorporar inmuebles RETIRADOS o VENDIDOS
    if (currentStatus != PropertyStatus.RETIRADO && currentStatus != PropertyStatus.VENDIDO) {
      throw new ConflictException(
          "Solo se pueden reincorporar inmuebles con estado RETIRADO o VENDIDO. Estado actual: "
              + currentStatus);
    }

    // Registrar en el historial de estados
    if (prop.getStatusHistory() == null) {
      prop.setStatusHistory(new ArrayList<>());
    }

    prop.getStatusHistory()
        .add(
            StatusHistory.builder()
                .oldStatus(currentStatus.name())
                .newStatus(PropertyStatus.DISPONIBLE.name())
                .changedAt(Instant.now())
                .changedBy(userId)
                .build());

    // Actualizar estado principal y limpiar campos de retiro
    prop.setStatus(PropertyStatus.DISPONIBLE);
    prop.setDeleted(false);
    prop.setUpdatedAt(Instant.now());
    prop.setMotivoRetiro(null);
    prop.setDetalleRetiro(null);

    // Si se reincorpora desde VENDIDO, cancelar la operación asociada
    if (currentStatus == PropertyStatus.VENDIDO) {
      try {
        var cancelledOperation =
            operationClient.cancelSoldOperationForProperty(id, userId, String.join(",", roles));
        if (cancelledOperation != null) {
          log.info(
              "Sold operation {} cancelled due to property reincorporation",
              cancelledOperation.id());
        }
      } catch (Exception e) {
        log.error("Could not cancel sold operation during reincorporation: {}", e.getMessage());
        throw new ConflictException(
            "No se pudo cancelar la operación vendida asociada durante la reincorporación.");
      }
    }

    log.info("Inmueble {} reincorporado al inventario por usuario {}", id, userId);
    return mapToResponse(propertyRepository.save(prop));
  }

  public List<PropertyResponse> findSuggestedProperties(String buscadorId) {
    // 1. Obtener preferencias del user-service
    UserClient.UserPreferenceResponse prefs = userClient.getPersonPreferences(buscadorId);

    if (prefs == null) return Collections.emptyList();

    // 2. Construir Query dinámica para MongoDB
    Query query = new Query();
    query.addCriteria(Criteria.where("status").is("DISPONIBLE"));
    query.addCriteria(Criteria.where("deleted").is(false));

    if (prefs.preferredZones() != null && !prefs.preferredZones().isEmpty()) {
      query.addCriteria(Criteria.where("zone").in(prefs.preferredZones()));
    }

    if (prefs.minRooms() != null || prefs.maxRooms() != null) {
      Criteria roomsCriteria = Criteria.where("rooms");
      if (prefs.minRooms() != null) {
        roomsCriteria = roomsCriteria.gte(prefs.minRooms());
      }
      if (prefs.maxRooms() != null) {
        roomsCriteria = roomsCriteria.lte(prefs.maxRooms());
      }
      query.addCriteria(roomsCriteria);
    }

    if (prefs.maxPrice() != null) {
      query.addCriteria(Criteria.where("price").lte(prefs.maxPrice()));
    }

    if (prefs.preferredPropertyType() != null && !prefs.preferredPropertyType().isBlank()) {
      query.addCriteria(Criteria.where("type").is(prefs.preferredPropertyType()));
    }

    return mongoTemplate.find(query, PropertyDocument.class).stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  /**
   * Genera un reporte detallado del inventario actual con un resumen ejecutivo de totales y cálculo
   * de días en inventario vigentes a la fecha actual para inmuebles activos.
   */
  public com.inmobiliaria.property_service.dto.response.InventoryReportResponse
      generateInventoryReport(String status, OperationType operationType) {

    // Obtener todos los inmuebles que no sufrieron un borrado lógico
    List<PropertyDocument> allActiveInventory = propertyRepository.findByDeletedFalse();

    // 1. Calcular totales globales para el Resumen Ejecutivo (Dashboard Indicators)
    Map<String, Long> totalsByStatus =
        allActiveInventory.stream()
            .collect(
                Collectors.groupingBy(
                    p -> p.getStatus() != null ? p.getStatus().name() : "SIN_ESPECIFICAR",
                    Collectors.counting()));

    Map<String, Long> totalsByOperationType =
        allActiveInventory.stream()
            .collect(
                Collectors.groupingBy(
                    p ->
                        p.getOperationType() != null
                            ? p.getOperationType().name()
                            : "SIN_ESPECIFICAR",
                    Collectors.counting()));

    // 2. Filtrar y transformar el listado detallado para la tabla del reporte
    List<com.inmobiliaria.property_service.dto.response.InventoryReportResponse.PropertyReportItem>
        reportItems =
            allActiveInventory.stream()
                .filter(
                    p ->
                        status == null
                            || status.isBlank()
                            || (p.getStatus() != null
                                && p.getStatus().name().equalsIgnoreCase(status)))
                .filter(p -> operationType == null || p.getOperationType() == operationType)
                .map(
                    p -> {
                      long days = 0;
                      String registrationDate = "N/A";
                      String exitDate = "--";

                      if (p.getCreatedAt() != null) {
                        registrationDate =
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                .withZone(java.time.ZoneId.of("UTC"))
                                .format(p.getCreatedAt());

                        Instant endDate =
                            Instant.now(); // Por defecto: fecha actual para inmuebles activos (PA3)

                        // Si el inmueble ya fue consolidado o retirado, calcular hasta su evento de
                        // cambio
                        if (p.getStatus() == PropertyStatus.VENDIDO
                            || p.getStatus() == PropertyStatus.RETIRADO) {
                          if (p.getStatusHistory() != null && !p.getStatusHistory().isEmpty()) {
                            endDate =
                                p.getStatusHistory().stream()
                                    .filter(sh -> sh.getNewStatus().equals(p.getStatus().name()))
                                    .max(Comparator.comparing(StatusHistory::getChangedAt))
                                    .map(StatusHistory::getChangedAt)
                                    .orElse(
                                        p.getUpdatedAt() != null
                                            ? p.getUpdatedAt()
                                            : Instant.now());
                          } else {
                            endDate = p.getUpdatedAt() != null ? p.getUpdatedAt() : Instant.now();
                          }

                          exitDate =
                              java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                  .withZone(java.time.ZoneId.of("UTC"))
                                  .format(endDate);
                        }

                        days =
                            java.time.temporal.ChronoUnit.DAYS.between(p.getCreatedAt(), endDate);
                        if (days < 0) days = 0;
                      }

                      return new com.inmobiliaria.property_service.dto.response
                          .InventoryReportResponse.PropertyReportItem(
                          p.getId(),
                          p.getTitle(),
                          p.getStatus() != null ? p.getStatus().name() : "N/A",
                          p.getOperationType() != null ? p.getOperationType().name() : "N/A",
                          p.getPrice(),
                          p.getZone() != null ? p.getZone() : "No especificada",
                          days,
                          registrationDate,
                          exitDate);
                    })
                .collect(Collectors.toList());

    return new com.inmobiliaria.property_service.dto.response.InventoryReportResponse(
        totalsByStatus, totalsByOperationType, reportItems);
  }

  private byte[] downloadFileBytes(String urlString) {
    if (urlString == null) return null;

    // Check if the URL is pointing to MinIO and try direct download first
    if (urlString.contains("localhost:9000")
        || urlString.contains("127.0.0.1:9000")
        || urlString.contains("minio:9000")) {
      try {
        java.net.URI uri = java.net.URI.create(urlString);
        String path = uri.getPath();
        if (path != null) {
          if (path.startsWith("/")) {
            path = path.substring(1);
          }
          String[] parts = path.split("/", 2);
          if (parts.length == 2) {
            String bucket = parts[0];
            String objectKey = parts[1];
            log.info(
                "Attempting to download file directly from MinIO: bucket={}, key={}",
                bucket,
                objectKey);
            try (java.io.InputStream in =
                minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
              byte[] bytes = in.readAllBytes();
              log.info("Successfully downloaded file directly from MinIO ({} bytes)", bytes.length);
              return bytes;
            }
          }
        }
      } catch (Exception e) {
        log.warn(
            "Failed to download directly from MinIO: {}. Falling back to HTTP.", e.getMessage());
      }
    }

    List<String> urlsToTry = new ArrayList<>();
    urlsToTry.add(urlString); // Try original URL first

    if (urlString.contains("localhost:9000")) {
      urlsToTry.add(urlString.replace("localhost:9000", "minio:9000"));
      urlsToTry.add(urlString.replace("localhost:9000", "127.0.0.1:9000"));
    } else if (urlString.contains("127.0.0.1:9000")) {
      urlsToTry.add(urlString.replace("127.0.0.1:9000", "minio:9000"));
      urlsToTry.add(urlString.replace("127.0.0.1:9000", "localhost:9000"));
    } else if (urlString.contains("minio:9000")) {
      urlsToTry.add(urlString.replace("minio:9000", "localhost:9000"));
      urlsToTry.add(urlString.replace("minio:9000", "127.0.0.1:9000"));
    }

    for (String targetUrl : urlsToTry) {
      try {
        log.info("Attempting to download file from: {}", targetUrl);
        java.net.URL url = java.net.URI.create(targetUrl).toURL();
        try (java.io.InputStream in = url.openStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
          byte[] buffer = new byte[4096];
          int n;
          while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
          }
          log.info("Successfully downloaded file from: {}", targetUrl);
          return out.toByteArray();
        }
      } catch (Exception e) {
        log.warn("Failed to download file from: {}. Error: {}", targetUrl, e.getMessage());
      }
    }

    log.error("Failed to download file from all attempted URLs for: {}", urlString);
    return null;
  }

  private byte[] generatePdfBytes(PropertyDocument property) throws Exception {
    String publicUrl = "http://localhost:5173/properties?selectedId=" + property.getId();
    String qrBase64 = QRCodeGenerator.generateQRCodeBase64(publicUrl, 200, 200);

    String mainImageUrl =
        property.getImages().stream()
            .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
            .map(img -> img.getPublicUrl())
            .findFirst()
            .orElse(
                property.getImageUrls() != null && !property.getImageUrls().isEmpty()
                    ? property.getImageUrls().get(0)
                    : null);

    String mainImageBase64 = null;
    if (mainImageUrl != null) {
      byte[] imgBytes = downloadFileBytes(mainImageUrl);
      if (imgBytes != null) {
        String mimeType = "image/jpeg";
        if (mainImageUrl.endsWith(".png")) mimeType = "image/png";
        else if (mainImageUrl.endsWith(".gif")) mimeType = "image/gif";
        mainImageBase64 =
            "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imgBytes);
      }
    }

    String agentName = "Support Team";
    String agentEmail = "support@drocainmob.com";
    String agentPhone = "N/A";

    if (property.getAssignedAgentId() != null && !property.getAssignedAgentId().isBlank()) {
      try {
        IdentityClient.UserResponse agent = identityClient.findById(property.getAssignedAgentId());
        if (agent != null) {
          agentName =
              agent.fullName() != null
                  ? agent.fullName()
                  : (agent.firstName() + " " + agent.lastName());
          agentEmail = agent.email() != null ? agent.email() : agentEmail;
          agentPhone = agent.phone() != null ? agent.phone() : agentPhone;
        }
      } catch (Exception e) {
        log.error("Failed to fetch agent details for id {}", property.getAssignedAgentId(), e);
      }
    }

    // Explicit currency format: e.g. $500,000.00
    String formattedPrice = "N/A";
    if (property.getPrice() != null) {
      java.text.DecimalFormat formatter =
          new java.text.DecimalFormat(
              "$#,##0.00", new java.text.DecimalFormatSymbols(java.util.Locale.US));
      formattedPrice = formatter.format(property.getPrice());
    }

    return pdfGenerationService.generatePropertyPdf(
        property, qrBase64, mainImageBase64, agentName, agentEmail, agentPhone, formattedPrice);
  }

  public byte[] generatePdf(String id, String adminId) throws Exception {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + id));

    byte[] pdfBytes = generatePdfBytes(property);

    AuditLog audit =
        AuditLog.builder()
            .userId(adminId)
            .action("PDF_GENERATED")
            .propertyId(id)
            .previousValue("N/A")
            .newValue("DOWNLOADED")
            .changes(new ArrayList<>())
            .timestamp(Instant.now())
            .build();
    auditLogRepository.save(audit);

    return pdfBytes;
  }

  public void sendPdfByEmail(
      String id,
      com.inmobiliaria.property_service.dto.request.SendPdfEmailRequest request,
      String adminId)
      throws Exception {
    PropertyDocument property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + id));

    byte[] pdfBytes = generatePdfBytes(property);
    String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

    String messageContent =
        request.message() != null && !request.message().isBlank()
            ? request.message()
            : "Hello, please find attached the details sheet for the property: "
                + property.getTitle();

    com.inmobiliaria.property_service.dto.request.SendAttachmentEmailRequest feignRequest =
        new com.inmobiliaria.property_service.dto.request.SendAttachmentEmailRequest(
            request.destinationEmail(),
            "Property Information: " + property.getTitle(),
            messageContent,
            pdfBase64,
            "property_sheet_" + id + ".pdf");

    notificationClient.sendEmailWithAttachment(feignRequest);

    AuditLog audit =
        AuditLog.builder()
            .userId(adminId)
            .action("PDF_SENT_EMAIL")
            .propertyId(id)
            .previousValue("N/A")
            .newValue(request.destinationEmail())
            .changes(new ArrayList<>())
            .timestamp(Instant.now())
            .build();
    auditLogRepository.save(audit);
  }
}

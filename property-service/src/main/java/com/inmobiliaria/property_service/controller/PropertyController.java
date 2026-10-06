package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.domain.StatusHistory;
import com.inmobiliaria.property_service.dto.request.AgentPropertyUpdateRequest;
import com.inmobiliaria.property_service.dto.request.AssignAgentRequest;
import com.inmobiliaria.property_service.dto.request.AssignOwnerRequest;
import com.inmobiliaria.property_service.dto.request.PropertyRequest;
import com.inmobiliaria.property_service.dto.request.RetirePropertyRequest;
import com.inmobiliaria.property_service.dto.request.SendPdfEmailRequest;
import com.inmobiliaria.property_service.dto.request.UpdateLocationRequest;
import com.inmobiliaria.property_service.dto.request.UpdateOperationTypeRequest;
import com.inmobiliaria.property_service.dto.request.UpdatePriceRequest;
import com.inmobiliaria.property_service.dto.request.UpdateStatusRequest;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import com.inmobiliaria.property_service.dto.response.InventoryMetricsResponse;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.dto.response.ResponsableResponse;
import com.inmobiliaria.property_service.dto.response.ResponseFactory;
import com.inmobiliaria.property_service.service.PropertyMetricsService;
import com.inmobiliaria.property_service.service.PropertyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/properties")
@RequiredArgsConstructor
public class PropertyController {

  private final PropertyService propertyService;
  private final PropertyMetricsService propertyMetricsService;
  private final ResponseFactory responseFactory;

  // --- READ OPERATIONS ---

  @GetMapping("/agent/{agentId}")
  public ResponseEntity<ApiResponse<List<PropertyResponse>>> findByAgent(
      @PathVariable String agentId) {
    List<PropertyResponse> data = propertyService.findByAgent(agentId);
    return ResponseEntity.ok(responseFactory.success("Properties found", data));
  }

  @PatchMapping("/{id}/agent-update")
  @PreAuthorize("hasRole('AGENT')")
  public ResponseEntity<ApiResponse<PropertyResponse>> updatePropertyAsAgent(
      @PathVariable String id,
      @Valid @RequestBody AgentPropertyUpdateRequest request,
      @RequestHeader("X-Auth-User-Id") String agentId) {
    PropertyResponse data = propertyService.updatePropertyAsAgent(id, request, agentId);
    return ResponseEntity.ok(responseFactory.success("Property updated successfully", data));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PropertyResponse>>> findAll(
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OperationType operationType,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false) @Positive Double minM2,
      @RequestParam(required = false) @Positive Double maxM2,
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false, defaultValue = "price") String sortBy,
      @RequestParam(required = false, defaultValue = "ASC") String sortOrder,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "9") int pageSize) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.ok(
          responseFactory.success("No properties found", Collections.emptyList()));
    }

    String currentUserId = (String) auth.getPrincipal();
    List<String> roles =
        auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

    var result =
        propertyService.findWithFilters(
            title,
            type,
            zone,
            status,
            operationType,
            minPrice,
            maxPrice,
            minM2,
            maxM2,
            agentId,
            currentUserId,
            roles,
            sortBy,
            sortOrder,
            page,
            pageSize);

    // Convert Map result to Page logic manually with safe casting
    Object dataObj = result.get("data");
    List<PropertyResponse> content = new ArrayList<>();
    if (dataObj instanceof List<?>) {
      for (Object item : (List<?>) dataObj) {
        if (item instanceof PropertyResponse) {
          content.add((PropertyResponse) item);
        }
      }
    }

    int total = 0;
    Object totalObj = result.get("totalElements");
    if (totalObj instanceof Number) {
      total = ((Number) totalObj).intValue();
    }

    return ResponseEntity.ok(
        responseFactory.paginated(
            "Properties retrieved successfully", content, page, pageSize, total));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PropertyResponse>> findById(@PathVariable String id) {
    PropertyResponse data = propertyService.findById(id);
    return ResponseEntity.ok(responseFactory.success("Property found", data));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> updateProperty(
      @PathVariable String id,
      @Valid @RequestBody PropertyRequest request,
      @RequestHeader("X-Auth-User-Id") String adminId) {
    PropertyResponse data = propertyService.updateProperty(id, request, adminId);
    return ResponseEntity.ok(responseFactory.success("Property updated successfully", data));
  }

  // --- WRITE OPERATIONS (PROPERTY AGGREGATE) ---

  @PostMapping
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> create(
      @Valid @RequestBody PropertyRequest request,
      @RequestHeader("X-Auth-User-Id") String agentId) {
    PropertyResponse data = propertyService.create(request, agentId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseFactory.created("Property created successfully", data));
  }

  @PatchMapping("/{id}/assign-owner")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> assignOwner(
      @PathVariable String id,
      @RequestBody AssignOwnerRequest request,
      @RequestHeader("X-Auth-User-Id") String adminId) {
    PropertyResponse data = propertyService.assignOwner(id, request.ownerId(), adminId);
    return ResponseEntity.ok(responseFactory.success("Owner assigned successfully", data));
  }

  @PatchMapping("/{id}/price")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> updatePrice(
      @PathVariable String id,
      @Valid @RequestBody UpdatePriceRequest request,
      @RequestHeader("X-Auth-User-Id") String adminId) {
    PropertyResponse data = propertyService.updatePrice(id, request.newPrice(), adminId);
    return ResponseEntity.ok(responseFactory.success("Price updated successfully", data));
  }

  @PatchMapping("/{id}/assign-agent")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> assignAgent(
      @PathVariable String id,
      @Valid @RequestBody AssignAgentRequest request,
      @RequestHeader("X-Auth-User-Id") String adminId) {
    PropertyResponse data = propertyService.assignAgent(id, request, adminId);
    return ResponseEntity.ok(responseFactory.success("Agent assigned successfully", data));
  }

  @PatchMapping("/{id}/operation-type")
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<PropertyResponse>> updateOperationType(
      @PathVariable String id, @Valid @RequestBody UpdateOperationTypeRequest request) {
    PropertyResponse data = propertyService.updateOperationType(id, request.operationType());
    return ResponseEntity.ok(responseFactory.success("Operation type updated successfully", data));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable String id, @RequestHeader("X-Auth-User-Id") String adminId) {
    propertyService.deleteProperty(id, adminId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .body(responseFactory.deleted("Property deleted successfully"));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or #isInternal")
  public ResponseEntity<ApiResponse<PropertyResponse>> updateStatus(
      @PathVariable String id,
      @Valid @RequestBody UpdateStatusRequest request,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader(value = "X-Internal-Call", defaultValue = "false") boolean isInternal) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    List<String> roles =
        auth != null
            ? auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
            : Collections.emptyList();

    PropertyResponse data =
        propertyService.updateStatus(id, request.status(), userId, roles, isInternal);
    return ResponseEntity.ok(responseFactory.success("Status updated successfully", data));
  }

  @GetMapping("/{id}/status-history")
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<List<StatusHistory>>> getStatusHistory(
      @PathVariable String id) {
    List<StatusHistory> data = propertyService.findById(id).statusHistory();
    return ResponseEntity.ok(responseFactory.success("Status history retrieved", data));
  }

  @GetMapping("/{id}/responsable")
  public ResponseEntity<ApiResponse<ResponsableResponse>> getResponsable(@PathVariable String id) {
    ResponsableResponse data = propertyService.getResponsable(id);
    return ResponseEntity.ok(responseFactory.success("Responsable retrieved", data));
  }

  @GetMapping("/owner/{ownerId}")
  public ResponseEntity<ApiResponse<List<PropertyResponse>>> findByOwner(
      @PathVariable String ownerId) {
    List<PropertyResponse> data = propertyService.findByOwner(ownerId);
    return ResponseEntity.ok(responseFactory.success("Properties found", data));
  }

  @PatchMapping("/{id}/location")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> updateLocation(
      @PathVariable String id,
      @Valid @RequestBody UpdateLocationRequest request,
      @RequestHeader("X-Auth-User-Id") String userId) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    List<String> roles =
        auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

    PropertyResponse data = propertyService.updateLocation(id, request, userId, roles);
    return ResponseEntity.ok(
        responseFactory.success("Ubicación geográfica actualizada correctamente", data));
  }

  // REINCORPORATE - Your feature (US-59 relist)
  @PostMapping("/{id}/reincorporate")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PropertyResponse>> reincorporate(
      @PathVariable String id,
      @RequestHeader("X-Auth-User-Id") String adminId,
      @RequestHeader("X-Auth-Roles") String rolesHeader) {

    List<String> roles = Arrays.asList(rolesHeader.replace("[", "").replace("]", "").split(","));
    PropertyResponse data = propertyService.reincorporateProperty(id, adminId, roles);
    return ResponseEntity.ok(
        responseFactory.success("Inmueble reincorporado exitosamente al inventario", data));
  }

  // RETIRAR - Their feature (track why listing got down) + Your feature integration
  @PostMapping("/{id}/retirar")
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<PropertyResponse>> retireProperty(
      @PathVariable String id,
      @Valid @RequestBody RetirePropertyRequest request,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Auth-Roles") String rolesHeader) {

    // Convertir el header a lista de roles con prefijo ROLE_
    List<String> roles =
        Arrays.stream(rolesHeader.split(","))
            .map(String::trim)
            .map(role -> "ROLE_" + role)
            .collect(Collectors.toList());

    PropertyResponse response = propertyService.retireProperty(id, request, userId, roles);
    return ResponseEntity.ok(responseFactory.success("Inmueble retirado correctamente", response));
  }

  @GetMapping("/filtrar")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<PropertyResponse>>> filterByBuscador(
      @RequestParam("buscador_id") String buscadorId) {
    List<PropertyResponse> suggestions = propertyService.findSuggestedProperties(buscadorId);

    String message =
        suggestions.isEmpty()
            ? "No se encontraron propiedades que coincidan con las preferencias del cliente."
            : "Se encontraron " + suggestions.size() + " propiedades sugeridas.";

    return ResponseEntity.ok(responseFactory.success(message, suggestions));
  }

  @GetMapping("/reporte-gerencial")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<
          ApiResponse<com.inmobiliaria.property_service.dto.response.InventoryReportResponse>>
      getInventoryReport(
          @RequestParam(required = false) String status,
          @RequestParam(required = false) OperationType operationType) {

    com.inmobiliaria.property_service.dto.response.InventoryReportResponse report =
        propertyService.generateInventoryReport(status, operationType);

    return ResponseEntity.ok(
        responseFactory.success("Reporte de inventario generado exitosamente", report));
  }

  // --- METRICS ENDPOINTS ---

  /**
   * Obtiene las métricas de tiempo en inventario sin filtros.
   *
   * @return InventoryMetricsResponse con todas las métricas calculadas
   */
  @GetMapping("/metrics/inventory")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<InventoryMetricsResponse>> getInventoryMetrics() {
    InventoryMetricsResponse metrics =
        propertyMetricsService.calculateInventoryMetrics(null, null, null);
    return ResponseEntity.ok(responseFactory.success("Inventory metrics calculated", metrics));
  }

  /**
   * Obtiene las métricas de tiempo en inventario filtradas por tipo de operación.
   *
   * @param operationType Tipo de operación: VENTA, ALQUILER, ANTICRETICO
   * @return InventoryMetricsResponse filtrada por tipo de operación
   */
  @GetMapping("/metrics/inventory/operation/{operationType}")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<InventoryMetricsResponse>> getInventoryMetricsByOperationType(
      @PathVariable String operationType) {
    InventoryMetricsResponse metrics =
        propertyMetricsService.calculateInventoryMetrics(operationType, null, null);
    return ResponseEntity.ok(
        responseFactory.success("Inventory metrics by operation type calculated", metrics));
  }

  /**
   * Obtiene las métricas de tiempo en inventario filtradas por zona geográfica.
   *
   * @param zone Zona geográfica
   * @return InventoryMetricsResponse filtrada por zona
   */
  @GetMapping("/metrics/inventory/zone/{zone}")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<InventoryMetricsResponse>> getInventoryMetricsByZone(
      @PathVariable String zone) {
    InventoryMetricsResponse metrics =
        propertyMetricsService.calculateInventoryMetrics(null, zone, null);
    return ResponseEntity.ok(
        responseFactory.success("Inventory metrics by zone calculated", metrics));
  }

  /**
   * Obtiene las métricas de tiempo en inventario filtradas por tipo de inmueble.
   *
   * @param propertyType Tipo de inmueble
   * @return InventoryMetricsResponse filtrada por tipo
   */
  @GetMapping("/metrics/inventory/type/{propertyType}")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<InventoryMetricsResponse>> getInventoryMetricsByPropertyType(
      @PathVariable String propertyType) {
    InventoryMetricsResponse metrics =
        propertyMetricsService.calculateInventoryMetrics(null, null, propertyType);
    return ResponseEntity.ok(
        responseFactory.success("Inventory metrics by property type calculated", metrics));
  }

  /**
   * Obtiene las métricas de tiempo en inventario con múltiples filtros.
   *
   * @param operationType Tipo de operación (opcional)
   * @param zone Zona geográfica (opcional)
   * @param propertyType Tipo de inmueble (opcional)
   * @return InventoryMetricsResponse con todos los filtros aplicados
   */
  @GetMapping("/metrics/inventory/filtered")
  @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<InventoryMetricsResponse>> getInventoryMetricsFiltered(
      @RequestParam(required = false) String operationType,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String propertyType) {
    InventoryMetricsResponse metrics =
        propertyMetricsService.calculateInventoryMetrics(operationType, zone, propertyType);
    return ResponseEntity.ok(responseFactory.success("Inventory metrics calculated", metrics));
  }

  @PostMapping("/{id}/generate-pdf")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> generatePdf(
      @PathVariable String id, @RequestHeader("X-Auth-User-Id") String adminId) throws Exception {
    byte[] pdfBytes = propertyService.generatePdf(id, adminId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", "property_sheet_" + id + ".pdf");
    return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
  }

  @PostMapping("/{id}/send-pdf")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> sendPdf(
      @PathVariable String id,
      @Valid @RequestBody SendPdfEmailRequest request,
      @RequestHeader("X-Auth-User-Id") String adminId)
      throws Exception {
    propertyService.sendPdfByEmail(id, request, adminId);
    return ResponseEntity.ok(
        responseFactory.success(
            "Property sheet email sent successfully to " + request.destinationEmail()));
  }
}

package com.inmobiliaria.contract_service.controller;

import com.inmobiliaria.contract_service.dto.ContractVersionResponse;
import com.inmobiliaria.contract_service.dto.CreateContractVersionRequest;
import com.inmobiliaria.contract_service.service.ContractVersionService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractVersionController {

  private final ContractVersionService contractVersionService;

  // ----------------------------------------------------------------
  // POST /api/contracts/{operationId}/versions
  // Registra una nueva versión de contrato (solo ADMINISTRADOR)
  // Cabeceras esperadas del API Gateway: X-User-Id, X-User-Role, X-User-Name
  // ----------------------------------------------------------------
  @PostMapping("/{operationId}/versions")
  public ResponseEntity<?> createVersion(
      @PathVariable String operationId,
      @Valid @RequestBody CreateContractVersionRequest request,
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Role", required = false) String userRole,
      @RequestHeader(value = "X-User-Name", required = false) String userName) {

    log.info(
        "POST /api/contracts/{}/versions - usuario: {}, rol: {}", operationId, userName, userRole);

    // Inyectar datos del usuario extraídos del JWT por el API Gateway
    request.setAuthorId(userId);
    request.setAuthorName(userName);
    request.setAuthorRole(userRole);

    try {
      ContractVersionResponse response =
          contractVersionService.createVersion(operationId, request, userRole);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);

    } catch (SecurityException e) {
      log.warn("Acceso denegado para usuario con rol '{}': {}", userRole, e.getMessage());
      Map<String, String> error = new HashMap<>();
      error.put("error", "Acceso denegado");
      error.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
  }

  // ----------------------------------------------------------------
  // GET /api/contracts/{operationId}/versions
  // Lista todas las versiones de un contrato
  // ----------------------------------------------------------------
  @GetMapping("/{operationId}/versions")
  public ResponseEntity<List<ContractVersionResponse>> getVersionsByOperation(
      @PathVariable String operationId) {

    log.info("GET /api/contracts/{}/versions", operationId);
    List<ContractVersionResponse> versions =
        contractVersionService.getVersionsByOperation(operationId);
    return ResponseEntity.ok(versions);
  }

  // ----------------------------------------------------------------
  // GET /api/contracts/versions/{versionId}
  // Obtiene el detalle completo de una versión específica
  // ----------------------------------------------------------------
  @GetMapping("/versions/{versionId}")
  public ResponseEntity<?> getVersionById(@PathVariable String versionId) {
    log.info("GET /api/contracts/versions/{}", versionId);

    try {
      ContractVersionResponse version = contractVersionService.getVersionById(versionId);
      return ResponseEntity.ok(version);

    } catch (RuntimeException e) {
      Map<String, String> error = new HashMap<>();
      error.put("error", "No encontrado");
      error.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
  }

  // ----------------------------------------------------------------
  // GET /api/contracts/health
  // Health check del servicio
  // ----------------------------------------------------------------
  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    Map<String, String> status = new HashMap<>();
    status.put("service", "contract-service");
    status.put("status", "UP");
    return ResponseEntity.ok(status);
  }
}

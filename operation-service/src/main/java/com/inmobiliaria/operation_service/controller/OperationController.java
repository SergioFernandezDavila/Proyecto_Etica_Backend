package com.inmobiliaria.operation_service.controller;

import com.inmobiliaria.operation_service.dto.AgentRankingResponse;
import com.inmobiliaria.operation_service.dto.OperationRequest;
import com.inmobiliaria.operation_service.dto.OperationResponse;
import com.inmobiliaria.operation_service.dto.response.ApiResponse;
import com.inmobiliaria.operation_service.dto.response.ResponseFactory;
import com.inmobiliaria.operation_service.service.OperationService;
import com.inmobiliaria.operation_service.service.ReportService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operations")
@RequiredArgsConstructor
public class OperationController {

  private final OperationService operationService;
  private final ReportService reportService;
  private final ResponseFactory responseFactory;

  @GetMapping
  public ResponseEntity<ApiResponse<List<OperationResponse>>> getAllOperations(
      @RequestHeader("X-Auth-User-Id") String userId, @RequestHeader("X-Auth-Roles") String roles) {
    return ResponseEntity.ok(
        responseFactory.success(
            "Operations retrieved successfully", operationService.findAll(userId, roles)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<OperationResponse>> getOperationById(@PathVariable String id) {
    return ResponseEntity.ok(
        responseFactory.success("Operation retrieved successfully", operationService.findById(id)));
  }

  @GetMapping("/property/{propertyId}")
  public ResponseEntity<ApiResponse<OperationResponse>> getOperationByPropertyId(
      @PathVariable String propertyId) {
    return ResponseEntity.ok(
        responseFactory.success(
            "Operation retrieved successfully", operationService.findByPropertyId(propertyId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<OperationResponse>> createOperation(
      @Valid @RequestBody OperationRequest request,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
      @RequestHeader(value = "X-Skip-Property-Sync", defaultValue = "false")
          boolean skipPropertySync) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            responseFactory.created(
                "Operation created successfully",
                operationService.create(request, userId, roles, !skipPropertySync)));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<OperationResponse>> updateStatus(
      @PathVariable String id,
      @RequestBody java.util.Map<String, String> body,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
    String status = body.get("status");
    return ResponseEntity.ok(
        responseFactory.success(
            "Operation status updated successfully",
            operationService.updateStatus(id, status, userId, roles)));
  }

  @PostMapping("/property/{propertyId}/sold/cancel")
  public ResponseEntity<ApiResponse<OperationResponse>> cancelSoldOperationForProperty(
      @PathVariable String propertyId,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
    return ResponseEntity.ok(
        responseFactory.success(
            "Sold operation cancelled successfully",
            operationService.cancelSoldOperationForProperty(propertyId, userId, roles)));
  }

  @GetMapping("/reports/agent-ranking")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<AgentRankingResponse>> getAgentRanking(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
      @RequestParam(required = false) String department) {
    return ResponseEntity.ok(
        responseFactory.success(
            "Ranking retrieved successfully",
            reportService.getAgentRanking(startDate, endDate, department)));
  }
}

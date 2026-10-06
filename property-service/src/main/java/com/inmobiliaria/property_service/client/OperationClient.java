package com.inmobiliaria.property_service.client;

import com.inmobiliaria.property_service.config.FeignConfig;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "operation-service", configuration = FeignConfig.class)
public interface OperationClient {

  @PostMapping("/operations")
  void createOperation(
      @RequestBody CreateOperationRequest request,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Auth-Roles") String roles,
      @RequestHeader("X-Skip-Property-Sync") boolean skipPropertySync);

  @GetMapping("/operations/property/{propertyId}")
  OperationResponse getOperationByPropertyId(@PathVariable String propertyId);

  @PatchMapping("/operations/{id}/status")
  void updateOperationStatus(
      @PathVariable String id,
      @RequestBody Map<String, String> status,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Auth-Roles") String roles);

  @PostMapping("/operations/property/{propertyId}/sold/cancel")
  OperationResponse cancelSoldOperationForProperty(
      @PathVariable String propertyId,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Auth-Roles") String roles);

  @Builder
  record CreateOperationRequest(
      String propertyId,
      String propertyName,
      String propertyType,
      String operationType,
      Double finalPrice,
      String currency,
      String clientId,
      String clientName,
      String agentId,
      String agentName,
      String ownerId,
      String ownerName,
      String department,
      String status,
      String notes,
      LocalDateTime closureDate) {}

  record OperationResponse(String id, String propertyId, String status, String agentId) {}
}

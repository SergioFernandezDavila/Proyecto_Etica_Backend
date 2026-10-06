package com.inmobiliaria.operation_service.client;

import com.inmobiliaria.operation_service.config.FeignConfig;
import com.inmobiliaria.operation_service.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "property-service", configuration = FeignConfig.class)
public interface PropertyClient {

  @GetMapping("/properties/{id}")
  ApiResponse<PropertyResponse> getProperty(@PathVariable("id") String id);

  @PatchMapping("/properties/{id}/status")
  void updateStatus(
      @PathVariable("id") String id,
      @RequestBody UpdateStatusRequest request,
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestHeader("X-Internal-Call") boolean isInternal);

  record UpdateStatusRequest(String status) {}

  record PropertyResponse(String id, String status, String title) {}

  @GetMapping("/properties/reporte-gerencial")
  ApiResponse<InventoryReportResponse> getInventoryReport();

  record InventoryReportResponse(java.util.Map<String, Long> totalsByStatus) {}
}

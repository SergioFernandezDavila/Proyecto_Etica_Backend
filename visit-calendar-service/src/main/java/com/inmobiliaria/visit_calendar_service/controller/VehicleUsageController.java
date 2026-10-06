package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.VehicleUsageReportResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.service.VehicleUsageService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/vehicles/usage-report")
public class VehicleUsageController {

  private final VehicleUsageService vehicleUsageService;
  private final ResponseFactory responseFactory;

  @GetMapping
  public ResponseEntity<ApiResponse<VehicleUsageReportResponse>> getUsageReport(
      @RequestParam(required = false) String vehicleId,
      @RequestParam Instant from,
      @RequestParam Instant to) {

    log.debug("GET /vehicles/usage-report: vehicleId={}, from={}, to={}", vehicleId, from, to);

    VehicleUsageReportResponse report = vehicleUsageService.generateReport(vehicleId, from, to);

    return ResponseEntity.ok(
        responseFactory.success("Reporte de uso de vehículos obtenido correctamente", report));
  }
}

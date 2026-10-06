package com.inmobiliaria.operation_service.controller;

import com.inmobiliaria.operation_service.dto.dashboard.DashboardResumenDto;
import com.inmobiliaria.operation_service.dto.response.ApiResponse;
import com.inmobiliaria.operation_service.dto.response.ResponseFactory;
import com.inmobiliaria.operation_service.service.DashboardService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

  private final DashboardService dashboardService;
  private final ResponseFactory responseFactory;

  @GetMapping("/resumen")
  public ResponseEntity<ApiResponse<DashboardResumenDto>> getResumen() {
    DashboardResumenDto resumen = dashboardService.obtenerResumenGlobal();
    return ResponseEntity.ok(responseFactory.success("Resumen obtenido exitosamente", resumen));
  }

  @GetMapping("/distribucion-estados")
  public ResponseEntity<ApiResponse<Map<String, Long>>> getDistribucionEstados() {
    Map<String, Long> distribucion = dashboardService.obtenerDistribucionEstados();
    return ResponseEntity.ok(
        responseFactory.success("Distribución de estados obtenida", distribucion));
  }
}

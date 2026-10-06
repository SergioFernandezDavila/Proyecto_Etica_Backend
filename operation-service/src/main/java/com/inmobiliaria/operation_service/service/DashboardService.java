package com.inmobiliaria.operation_service.service;

import com.inmobiliaria.operation_service.client.PropertyClient;
import com.inmobiliaria.operation_service.client.VisitClient;
import com.inmobiliaria.operation_service.dto.dashboard.DashboardResumenDto;
import com.inmobiliaria.operation_service.repository.OperationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

  private final PropertyClient propertyClient;
  private final VisitClient visitClient;
  private final OperationRepository operationRepository;

  @Cacheable(value = "dashboardCache", key = "'resumen'")
  public DashboardResumenDto obtenerResumenGlobal() {
    log.info("Calculando resumen global del dashboard (Sin Caché)");

    // 1. Total de Inmuebles Activos (Excluyendo VENDIDO, ELIMINADO, RETIRADO)
    var propertyReport = propertyClient.getInventoryReport();
    long inmueblesActivos = 0;
    if (propertyReport != null && propertyReport.getData() != null) {
      Map<String, Long> totals = propertyReport.getData().totalsByStatus();
      if (totals != null) {
        inmueblesActivos =
            totals.getOrDefault("DISPONIBLE", 0L)
                + totals.getOrDefault("RESERVADO", 0L)
                + totals.getOrDefault("EN_NEGOCIACION", 0L);
      }
    }

    // 2. Visitas programadas para el resto de la semana
    Instant now = Instant.now();
    Instant endOfWeek = now.plus(7, ChronoUnit.DAYS); // Aproximación a 7 días
    int totalVisitas = 0;
    try {
      var visitResponse = visitClient.getCalendar(now, endOfWeek);
      if (visitResponse != null && visitResponse.getData() != null) {
        totalVisitas = visitResponse.getData().totalEvents();
      }
    } catch (Exception e) {
      log.error("Error obteniendo visitas del calendario: {}", e.getMessage());
    }

    // 3. Operaciones en curso (PENDING, ACTIVE)
    long operacionesEnCurso = operationRepository.countByStatusIn(List.of("PENDING", "ACTIVE"));

    return DashboardResumenDto.builder()
        .totalInmueblesActivos(inmueblesActivos)
        .visitasProgramadasSemana(totalVisitas)
        .operacionesEnCurso(operacionesEnCurso)
        .build();
  }

  @Cacheable(value = "dashboardCache", key = "'distribucion'")
  public Map<String, Long> obtenerDistribucionEstados() {
    log.info("Calculando distribución de estados (Sin Caché)");
    var propertyReport = propertyClient.getInventoryReport();
    if (propertyReport != null && propertyReport.getData() != null) {
      return propertyReport.getData().totalsByStatus();
    }
    return Map.of();
  }
}

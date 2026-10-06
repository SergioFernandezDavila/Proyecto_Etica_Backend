package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.model.AlertConfig;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.service.AlertConfigService;
import com.inmobiliaria.visit_calendar_service.service.DailySummaryService;
import com.inmobiliaria.visit_calendar_service.service.UpcomingVisitsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/alertas")
public class AlertasController {

  private final UpcomingVisitsService upcomingVisitsService;
  private final AlertConfigService alertConfigService;
  private final DailySummaryService dailySummaryService;
  private final ResponseFactory responseFactory;

  // Endpoint para obtener visitas próximas (recordatorios individuales)
  @GetMapping("/visitas-proximas")
  public ResponseEntity<ApiResponse<List<Visit>>> getUpcomingVisits(
      @RequestHeader("X-Auth-User-Id") String userId,
      @RequestParam(required = false) Integer hours) {
    List<Visit> visits = upcomingVisitsService.getUpcomingVisitsForUser(userId, hours);
    return ResponseEntity.ok(responseFactory.success("Próximas visitas obtenidas", visits));
  }

  // Endpoint para obtener el resumen de visitas del día actual
  @GetMapping("/visitas-del-dia")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<List<Visit>>> getTodayVisits() {
    List<Visit> visits = dailySummaryService.getTodayVisits();
    return ResponseEntity.ok(responseFactory.success("Visitas del día obtenidas", visits));
  }

  @GetMapping("/admin/configuracion-alertas")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<AlertConfig>> getAlertConfig() {
    AlertConfig config = alertConfigService.getConfig();
    return ResponseEntity.ok(responseFactory.success("Configuración obtenida", config));
  }

  // Endpoint para actualizar la configuración de alertas
  @PutMapping("/admin/configuracion-alertas")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<AlertConfig>> updateAlertConfig(
      @RequestParam boolean enableDailySummary,
      @RequestParam boolean enableIndividualReminders,
      @RequestParam int anticipationMinutes,
      @RequestParam(required = false) String channel) {

    if (anticipationMinutes != 30 && anticipationMinutes != 60 && anticipationMinutes != 90) {
      throw new IllegalArgumentException("El tiempo de anticipación debe ser 30, 60 o 90 minutos");
    }

    AlertConfig updated =
        alertConfigService.updateConfig(
            enableDailySummary, enableIndividualReminders, anticipationMinutes, channel);

    // Si se habilitó el resumen diario y aún no se envió hoy, enviarlo inmediatamente
    if (enableDailySummary) {
      dailySummaryService.sendImmediateSummaryIfNeeded();
    }

    return ResponseEntity.ok(responseFactory.success("Configuración actualizada", updated));
  }
}

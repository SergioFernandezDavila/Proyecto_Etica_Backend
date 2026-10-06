package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.client.NotificationClient;
import com.inmobiliaria.visit_calendar_service.client.UserAdminClient;
import com.inmobiliaria.visit_calendar_service.domain.InteractionType;
import com.inmobiliaria.visit_calendar_service.dto.request.SendInAppNotificationRequest;
import com.inmobiliaria.visit_calendar_service.model.AlertConfig;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

  private final VisitRepository visitRepository;
  private final UserAdminClient userAdminClient;
  private final NotificationClient notificationClient;
  private final AlertConfigService alertConfigService;

  /**
   * Envía el resumen diario a todos los administradores que tengan habilitada la opción. Se ejecuta
   * mediante scheduler a las 08:00.
   */
  public void sendDailySummaries() {
    AlertConfig config = alertConfigService.getConfig();
    if (!config.isEnableDailySummary()) {
      log.debug("Resumen diario deshabilitado. No se enviarán notificaciones.");
      return;
    }

    // Evitar duplicados si el scheduler se ejecuta más de una vez el mismo día
    LocalDate today = LocalDate.now();
    if (config.getLastDailyNotificationDate() != null
        && config.getLastDailyNotificationDate().equals(today)) {
      log.info("Resumen diario ya enviado hoy. No se reenviará.");
      return;
    }

    // Obtener todas las visitas programadas para hoy
    Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant endOfDay = today.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
    List<Visit> todayVisits = visitRepository.findByStartTimeBetween(startOfDay, endOfDay);
    int totalVisits = todayVisits.size();

    // Obtener la lista de administradores desde user-service
    List<String> adminUserIds = fetchAdminUserIds();
    if (adminUserIds.isEmpty()) {
      log.warn("No se encontraron administradores para enviar el resumen diario.");
      return;
    }

    // Enviar notificación a cada administrador
    for (String adminId : adminUserIds) {
      sendSummaryToAdmin(adminId, totalVisits);
    }

    // Marcar como enviado hoy
    alertConfigService.markDailyNotificationSent();
    log.info(
        "Resumen diario enviado a {} administradores. Total visitas: {}",
        adminUserIds.size(),
        totalVisits);
  }

  /**
   * Envía el resumen inmediatamente cuando un administrador activa la opción después de las 08:00.
   */
  public void sendImmediateSummaryIfNeeded() {
    AlertConfig config = alertConfigService.getConfig();
    if (!config.isEnableDailySummary()) return;

    LocalDate today = LocalDate.now();
    if (config.getLastDailyNotificationDate() != null
        && config.getLastDailyNotificationDate().equals(today)) {
      return; // Ya se envió hoy
    }

    // Verificar si ya pasaron las 08:00 AM
    LocalTime now = LocalTime.now();
    if (now.isAfter(LocalTime.of(8, 0))) {
      log.info("Activación después de las 08:00 – enviando resumen inmediato.");
      sendDailySummaries();
    }
  }

  /**
   * Obtiene el listado de visitas del día actual (para el endpoint GET /alertas/visitas-del-dia).
   */
  public List<Visit> getTodayVisits() {
    LocalDate today = LocalDate.now();
    Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant endOfDay = today.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
    return visitRepository.findByStartTimeBetween(startOfDay, endOfDay);
  }

  private List<String> fetchAdminUserIds() {
    try {
      var response = userAdminClient.getAdmins();
      var data = (List<Map<String, Object>>) response.get("data");
      if (data == null) return List.of();
      return data.stream().map(user -> (String) user.get("id")).filter(Objects::nonNull).toList();
    } catch (Exception e) {
      log.error("Error al obtener administradores desde identity-service: {}", e.getMessage());
      return List.of();
    }
  }

  private void sendSummaryToAdmin(String adminId, int totalVisits) {
    String subject = "📋 Resumen de visitas del día";
    String content =
        String.format(
            "Tienes %d visita(s) programada(s) para hoy. Revisa los detalles en el panel.",
            totalVisits);
    Map<String, Object> details =
        Map.of("totalVisits", totalVisits, "date", LocalDate.now().toString());

    SendInAppNotificationRequest request =
        new SendInAppNotificationRequest(
            adminId,
            "DAILY_VISIT_SUMMARY",
            InteractionType.ADMIN_OP,
            List.of(),
            subject,
            content,
            details);

    try {
      notificationClient.sendInAppNotification(request);
      log.info("Resumen diario enviado a administrador {}", adminId);
    } catch (Exception e) {
      log.error("Error al enviar resumen diario a {}: {}", adminId, e.getMessage());
    }
  }
}

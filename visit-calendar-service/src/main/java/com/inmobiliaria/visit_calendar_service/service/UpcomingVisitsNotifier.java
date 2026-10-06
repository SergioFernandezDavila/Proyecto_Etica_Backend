package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.client.NotificationClient;
import com.inmobiliaria.visit_calendar_service.client.UserAdminClient;
import com.inmobiliaria.visit_calendar_service.domain.InteractionType;
import com.inmobiliaria.visit_calendar_service.dto.request.SendInAppNotificationRequest;
import com.inmobiliaria.visit_calendar_service.model.AlertConfig;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpcomingVisitsNotifier {

  private final VisitRepository visitRepository;
  private final AlertConfigService alertConfigService;
  private final NotificationClient notificationClient;
  private final UserAdminClient userAdminClient; // Inyectar el cliente Feign

  @Scheduled(fixedDelay = 30 * 1000) // cada 30 segundos
  @Transactional
  public void processUpcomingVisits() {
    AlertConfig config = alertConfigService.getConfig();
    if (!config.isEnableIndividualReminders()) {
      log.debug("Recordatorios de visitas próximas deshabilitados (para administradores).");
      return;
    }

    Instant now = Instant.now();
    int minutes = config.getAnticipationMinutes();
    Instant limit = now.plus(minutes, ChronoUnit.MINUTES);

    List<Visit> upcomingVisits =
        visitRepository.findByStartTimeBetweenAndStatus(now, limit, Visit.EventStatus.SCHEDULED);
    if (upcomingVisits.isEmpty()) {
      return;
    }

    // Obtener lista de administradores
    List<String> adminIds = fetchAdminUserIds();
    if (adminIds.isEmpty()) {
      log.warn("No se encontraron administradores para enviar recordatorios de visitas próximas.");
      return;
    }

    // Para cada visita próxima, notificar a todos los administradores
    for (Visit visit : upcomingVisits) {
      if (visit.isUpcomingNotificationSent()) continue;

      for (String adminId : adminIds) {
        sendNotificationToAdmin(adminId, visit);
      }

      visit.setUpcomingNotificationSent(true);
      visitRepository.save(visit);
      log.info(
          "Notificación de visita próxima enviada a {} administradores para la visita ID {}",
          adminIds.size(),
          visit.getId());
    }
  }

  private void sendNotificationToAdmin(String adminId, Visit visit) {
    try {
      String formattedStart = formatLocalDateTime(visit.getStartTime());
      String subject = "📅 Visita próxima a realizarse";
      String content =
          String.format(
              "La visita programada para la propiedad '%s' con el agente '%s' está próxima a comenzar a las %s.",
              visit.getPropertyName(), visit.getAgentName(), formattedStart);

      // Construir lista de involucrados sin elementos nulos
      List<String> involvedUserIds = new ArrayList<>();
      if (visit.getAgentId() != null) involvedUserIds.add(visit.getAgentId());
      if (visit.getClientId() != null) involvedUserIds.add(visit.getClientId());

      SendInAppNotificationRequest request =
          new SendInAppNotificationRequest(
              adminId,
              "UPCOMING_VISIT",
              InteractionType.ADMIN_OP,
              involvedUserIds,
              subject,
              content,
              Map.of(
                  "visitId", visit.getId(),
                  "propertyId", visit.getPropertyId(),
                  "agentId", visit.getAgentId(),
                  "clientId", visit.getClientId() != null ? visit.getClientId() : "",
                  "startTime", visit.getStartTime().toString()));

      notificationClient.sendInAppNotification(request);
    } catch (Exception e) {
      log.error(
          "Error enviando notificación de visita próxima al admin {}: {}",
          adminId,
          e.getMessage(),
          e);
    }
  }

  private List<String> fetchAdminUserIds() {
    try {
      var response = userAdminClient.getAdmins(); // Usar el nuevo endpoint
      var data = (List<Map<String, Object>>) response.get("data");
      if (data == null) return List.of();
      return data.stream().map(user -> (String) user.get("id")).filter(Objects::nonNull).toList();
    } catch (Exception e) {
      log.error("Error al obtener administradores desde identity-service: {}", e.getMessage());
      return List.of();
    }
  }

  private String formatLocalDateTime(Instant instant) {
    if (instant == null) return "";
    ZoneId boliviaZone = ZoneId.of("America/La_Paz");
    ZonedDateTime zdt = instant.atZone(boliviaZone);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    return zdt.format(formatter);
  }
}

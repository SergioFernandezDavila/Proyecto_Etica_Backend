package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.client.NotificationClient;
import com.inmobiliaria.visit_calendar_service.domain.InteractionType;
import com.inmobiliaria.visit_calendar_service.dto.SendNotificationRequest;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.CreateVisitRequest;
import com.inmobiliaria.visit_calendar_service.dto.request.SendInAppNotificationRequest;
import com.inmobiliaria.visit_calendar_service.model.VisitRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationService {

  private final NotificationClient notificationClient;

  // Notifica al agente por solicitud de visita (ya existente)
  public boolean notifyAgentOfVisitRequest(VisitRequest visitRequest) {
    try {
      Map<String, Object> payload =
          Map.of(
              "type", "VISIT_REQUEST",
              "recipientId", visitRequest.getAgentId(),
              "subject", "Nueva solicitud de visita - " + visitRequest.getPropertyName(),
              "message", buildNotificationMessage(visitRequest),
              "metadata",
                  Map.of(
                      "visitRequestId", visitRequest.getId(),
                      "propertyId", visitRequest.getPropertyId(),
                      "propertyName", visitRequest.getPropertyName(),
                      "clientName", visitRequest.getClientName(),
                      "clientEmail", visitRequest.getClientEmail(),
                      "preferredDate", visitRequest.getPreferredDateTime().toString()));

      ResponseEntity<String> response = notificationClient.notifyVisitRequest(payload);
      if (response.getStatusCode().is2xxSuccessful()) {
        log.info(
            "Notificación enviada al agente {} para la propiedad {}",
            visitRequest.getAgentId(),
            visitRequest.getPropertyName());
        return true;
      }
    } catch (Exception e) {
      log.warn(
          "No se pudo conectar con el notification-service. La solicitud se guardó igualmente. Error: {}",
          e.getMessage());
    }
    log.info(
        "[NOTIFICACIÓN INTERNA] Agente '{}' — Nueva solicitud de visita de '{}' para el inmueble '{}' el {}",
        visitRequest.getAgentName(),
        visitRequest.getClientName(),
        visitRequest.getPropertyName(),
        visitRequest.getPreferredDateTime());
    return false;
  }

  // Notifica al propietario del inmueble cuando se programa una visita
  public void notifyPropertyOwner(String ownerEmail, String ownerName, CreateVisitRequest visit) {
    SendNotificationRequest req =
        new SendNotificationRequest(
            ownerEmail,
            "VISIT_SCHEDULED",
            "Nueva visita programada - " + visit.getPropertyName(),
            buildPropertyOwnerMessage(ownerName, visit),
            "EMAIL");
    notificationClient.sendNotification(req);
  }

  public void sendInAppNotificationToOwner(
      String ownerId,
      String ownerName,
      String propertyName,
      String subject,
      String content,
      InteractionType interactionType,
      Map<String, Object> details) {
    try {
      SendInAppNotificationRequest request =
          new SendInAppNotificationRequest(
              ownerId, // recipientId
              "VISIT_" + interactionType.name(), // type
              interactionType,
              null, // involvedUserIds
              subject,
              content,
              details);
      notificationClient.sendInAppNotification(request);
      log.info("In-app notification sent to owner {}: {}", ownerId, subject);
    } catch (Exception e) {
      log.warn("Failed to send in-app notification to owner {}: {}", ownerId, e.getMessage());
    }
  }

  private String buildPropertyOwnerMessage(String ownerName, CreateVisitRequest visit) {
    return String.format(
        "Estimado/a %s,\n\nSe ha programado una visita a su propiedad '%s'.\n\n"
            + "Detalles:\n• Dirección: %s\n• Fecha y hora: %s - %s\n• Agente a cargo: %s (%s)\n• Notas: %s\n\n"
            + "Puede consultar el historial de visitas en el sistema.",
        ownerName,
        visit.getPropertyName(),
        visit.getPropertyAddress() != null ? visit.getPropertyAddress() : "No especificada",
        visit.getStartTime(),
        visit.getEndTime(),
        visit.getAgentName(),
        visit.getAgentId(),
        visit.getNotes() != null ? visit.getNotes() : "Sin observaciones");
  }

  private String buildNotificationMessage(VisitRequest r) {
    return String.format(
        "El cliente %s (%s) ha solicitado una visita al inmueble '%s'.\n"
            + "Horario preferido: %s\n"
            + "%s"
            + "Mensaje del cliente: %s\n\n"
            + "Por favor ingresa al sistema para aceptar o rechazar la solicitud.",
        r.getClientName(),
        r.getClientEmail(),
        r.getPropertyName(),
        r.getPreferredDateTime(),
        r.getAlternativeDateTime() != null
            ? "Horario alternativo: " + r.getAlternativeDateTime() + "\n"
            : "",
        r.getMessage() != null ? r.getMessage() : "(Sin mensaje)");
  }
}

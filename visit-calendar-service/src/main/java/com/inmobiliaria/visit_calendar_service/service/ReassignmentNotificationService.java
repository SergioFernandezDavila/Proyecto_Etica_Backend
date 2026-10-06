package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.client.NotificationClient;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Notification service for appointment reassignment events.
 *
 * <p>Makes real HTTP calls to the team's existing notification-service (port 8083) using OpenFeign.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReassignmentNotificationService {

  private final NotificationClient notificationClient;

  // ─────────────────────────────────────────────────────────────────────────
  // NOTIFY TARGET AGENT — incoming reassignment request
  // ─────────────────────────────────────────────────────────────────────────

  public boolean notifyReassignmentRequest(ReassignmentRequest request, CalendarEvent event) {
    try {
      Map<String, Object> payload =
          Map.of(
              "type",
              "REASSIGNMENT_REQUEST",
              "recipientId",
              request.getDestinationAgentId(),
              "subject",
              "New reassignment request — Appointment ID: " + request.getVisitId(),
              "message",
              buildRequestMessage(request, event),
              "metadata",
              Map.of(
                  "reassignmentRequestId", request.getId(),
                  "visitId", request.getVisitId(),
                  "requestingAgentId", request.getRequestingAgentId(),
                  "destinationAgentId", request.getDestinationAgentId(),
                  "reason", request.getReason()));

      ResponseEntity<String> response = notificationClient.notifyReassignmentRequest(payload);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info(
            "[REASSIGNMENT] Request notification sent to agent '{}' for visit '{}'.",
            request.getDestinationAgentId(),
            request.getVisitId());
        return true;
      }
    } catch (Exception e) {
      log.warn("[REASSIGNMENT] Could not reach notification-service. Error: {}", e.getMessage());
    }

    log.info(
        "[REASSIGNMENT - INTERNAL] Agent '{}' received a reassignment request from agent '{}' for visit '{}'.",
        request.getDestinationAgentId(),
        request.getRequestingAgentId(),
        request.getVisitId());

    return false;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // NOTIFY REQUESTING AGENT — accept / reject decision
  // ─────────────────────────────────────────────────────────────────────────

  public boolean notifyReassignmentDecision(ReassignmentRequest request) {
    try {
      Map<String, Object> payload =
          Map.of(
              "type",
              "REASSIGNMENT_DECISION",
              "recipientId",
              request.getRequestingAgentId(),
              "subject",
              "Your reassignment request was "
                  + request.getStatus().name()
                  + " — Appointment ID: "
                  + request.getVisitId(),
              "message",
              buildDecisionMessage(request),
              "metadata",
              Map.of(
                  "reassignmentRequestId",
                  request.getId(),
                  "visitId",
                  request.getVisitId(),
                  "destinationAgentId",
                  request.getDestinationAgentId(),
                  "decision",
                  request.getStatus().name(),
                  "responseComment",
                  request.getCommentReply() != null ? request.getCommentReply() : ""));

      ResponseEntity<String> response = notificationClient.notifyReassignmentDecision(payload);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info(
            "[REASSIGNMENT] Decision notification ('{}') sent to agent '{}'.",
            request.getStatus().name(),
            request.getRequestingAgentId());
        return true;
      }
    } catch (Exception e) {
      log.warn("[REASSIGNMENT] Could not reach notification-service. Error: {}", e.getMessage());
    }

    log.info(
        "[REASSIGNMENT - INTERNAL] Agent '{}' — your reassignment request for visit '{}' was {}.",
        request.getRequestingAgentId(),
        request.getVisitId(),
        request.getStatus().name());

    return false;
  }

  private String buildRequestMessage(ReassignmentRequest request, CalendarEvent event) {
    return String.format(
        "Agent '%s' has requested that you take over the following appointment:\n\n"
            + "  • Appointment ID : %s\n"
            + "  • Property       : %s\n"
            + "  • Scheduled date : %s\n"
            + "  • Reason         : %s\n\n"
            + "Please log in to the system to accept or reject this request.",
        request.getRequestingAgentId(),
        request.getVisitId(),
        event.getPropertyName(),
        event.getStartTime() != null ? event.getStartTime().toString() : "N/A",
        request.getReason());
  }

  private String buildDecisionMessage(ReassignmentRequest request) {
    String decisionLabel =
        "ACCEPTED".equals(request.getStatus().name()) ? "accepted ✓" : "rejected ✗";

    String commentSection =
        (request.getCommentReply() != null && !request.getCommentReply().isBlank())
            ? "\n  • Comment : " + request.getCommentReply()
            : "";

    String outcome =
        "ACCEPTED".equals(request.getStatus().name())
            ? "The appointment has been transferred to the other agent's schedule."
            : "The appointment remains assigned to you — no changes were made.";

    return String.format(
        "Your reassignment request for appointment '%s' was %s by agent '%s'.%s\n\n%s",
        request.getVisitId(),
        decisionLabel,
        request.getDestinationAgentId(),
        commentSection,
        outcome);
  }
}

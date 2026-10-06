package com.inmobiliaria.visit_calendar_service.dto;

import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.model.Visit.EventStatus;
import com.inmobiliaria.visit_calendar_service.model.Visit.EventType;
import java.time.Instant;
import lombok.Data;

/**
 * Response body for POST /visits/{id}/reschedule
 *
 * <p>Returns the newly created visit along with a reference to the original cancelled visit, so the
 * frontend can render a link.
 *
 * <p>PA: The new visit keeps the reference to the client, property, and the original cancelled
 * visit (originVisitId). All fields from the original visit are preserved and included in the
 * response.
 */
@Data
public class RescheduleResponse {

  // ── New visit data ────────────────────────────────────────────────────
  private String newVisitId;
  private String propertyId;
  private String propertyName;
  private String propertyAddress;
  private String clientId;
  private String clientName;
  private String agentId;
  private String agentName;
  private Instant startTime;
  private Instant endTime;
  private EventType type;
  private EventStatus status;
  private String notes;
  private Boolean ownEvent;
  private Instant createdAt;

  // ── Link back to the original cancelled visit ─────────────────────────
  /** ID of the original cancelled visit that was rescheduled */
  private String originVisitId;

  /** Convenience message shown in the UI */
  private String message;

  // ── Factory ───────────────────────────────────────────────────────────

  public static RescheduleResponse from(Visit newVisit, String message) {
    RescheduleResponse dto = new RescheduleResponse();
    dto.newVisitId = newVisit.getId();
    dto.propertyId = newVisit.getPropertyId();
    dto.propertyName = newVisit.getPropertyName();
    dto.propertyAddress = newVisit.getPropertyAddress();
    dto.clientId = newVisit.getClientId();
    dto.clientName = newVisit.getClientName();
    dto.agentId = newVisit.getAgentId();
    dto.agentName = newVisit.getAgentName();
    dto.startTime = newVisit.getStartTime();
    dto.endTime = newVisit.getEndTime();
    dto.type = newVisit.getType();
    dto.status = newVisit.getStatus();
    dto.notes = newVisit.getNotes();
    dto.ownEvent = newVisit.getOwnEvent();
    dto.createdAt = newVisit.getCreatedAt();
    dto.originVisitId = newVisit.getOriginVisitId();
    dto.message = message;
    return dto;
  }
}

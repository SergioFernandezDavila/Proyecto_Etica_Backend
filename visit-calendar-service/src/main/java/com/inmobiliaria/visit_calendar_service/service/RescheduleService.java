package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.dto.RescheduleRequest;
import com.inmobiliaria.visit_calendar_service.dto.RescheduleResponse;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.ReschedulingHistory;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.model.Visit.EventStatus;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business logic for rescheduling a cancelled visit.
 *
 * <p>TD: Implement RescheduleService with full business logic and 409 guard.
 *
 * <p>Flow: 1. Load the original visit — 404 if not found. 2. Guard: visit must be CANCELLED — 409
 * if not (PA3). 3. Availability check: agent and property must be free in a 1-hour window. 4.
 * Create a new SCHEDULED visit copying client/property/agent from the original. 5. Set
 * originVisitId on the new visit to link it back (PA2). 6. Append a ReschedulingHistory to the
 * original visit's history. 7. Save both documents and return the response.
 */
@Slf4j
@Service
public class RescheduleService {

  /**
   * Buffer around the proposed datetime used to detect scheduling conflicts. A visit blocks 60
   * minutes before and after its scheduled time.
   */
  private static final long AVAILABILITY_BUFFER_MINUTES = 60L;

  private final VisitRepository visitRepository;
  private final CalendarEventRepository calendarEventRepository;
  private final AgentAvailabilityService agentAvailabilityService;

  public RescheduleService(
      VisitRepository visitRepository,
      CalendarEventRepository calendarEventRepository,
      AgentAvailabilityService agentAvailabilityService) {
    this.visitRepository = visitRepository;
    this.calendarEventRepository = calendarEventRepository;
    this.agentAvailabilityService = agentAvailabilityService;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // RESCHEDULE — main entry point
  // POST /visits/{id}/reschedule
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Creates a new SCHEDULED visit rescheduled from a CANCELLED one.
   *
   * @param originalVisitId ID of the cancelled visit to reschedule
   * @param agentId Authenticated agent performing the action (from JWT)
   * @param request New datetime and optional notes
   * @return RescheduleResponse with the new visit data and originVisitId
   * @throws ResponseStatusException 404 if the visit does not exist
   * @throws ResponseStatusException 409 if the visit is not in CANCELLED status (PA3)
   * @throws ResponseStatusException 422 if the agent or property is unavailable
   */
  public RescheduleResponse reschedule(
      String originalVisitId, String agentId, RescheduleRequest request) {

    // 1. Load original visit
    Visit original =
        visitRepository
            .findById(originalVisitId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Visit not found: " + originalVisitId));

    // 2. Status guard — PA3: cannot reschedule a COMPLETED or SCHEDULED visit
    if (original.getStatus() != EventStatus.CANCELLED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Only cancelled visits can be rescheduled. "
              + "Current status: "
              + original.getStatus().name());
    }

    Instant newEndTime =
        request.getNewEndTime() != null
            ? request.getNewEndTime()
            : request
                .getNewStartTime()
                .plus(
                    ChronoUnit.MINUTES.between(original.getStartTime(), original.getEndTime()),
                    ChronoUnit.MINUTES);
    request.setNewEndTime(newEndTime);

    // 3. Availability check — agent working hours (Sprint 5)
    agentAvailabilityService.checkAgentAvailability(
        original.getAgentId(), request.getNewStartTime(), newEndTime);

    // 3b. Availability check — agent visit overlaps
    validateAgentAvailability(original.getAgentId(), request.getNewStartTime(), null);

    // 4. Availability check — property
    validatePropertyAvailability(original.getPropertyId(), request.getNewStartTime(), null);

    // 5. Build the new visit (PA2: copy client, property, agent — add
    // originVisitId)
    Visit newVisit = buildNewVisit(original, request, agentId);
    newVisit = visitRepository.save(newVisit);
    log.info(
        "[RescheduleService] New visit created: id='{}', origin='{}'",
        newVisit.getId(),
        originalVisitId);

    // Save to CalendarEventRepository
    CalendarEvent calendarEvent = convertVisitToCalendarEvent(newVisit);
    calendarEventRepository.save(calendarEvent);
    log.info(
        "[RescheduleService] Calendar event saved: id='{}', originVisitId='{}'",
        calendarEvent.getId(),
        originalVisitId);

    // 6. Append rescheduling record to the original visit's history
    ReschedulingHistory record =
        ReschedulingHistory.builder()
            .newVisitId(newVisit.getId())
            .previousStartTime(original.getStartTime())
            .previousEndTime(original.getEndTime())
            .newStartTime(request.getNewStartTime())
            .newEndTime(request.getNewEndTime())
            .rescheduledByAgentId(agentId)
            .rescheduledAt(Instant.now())
            .build();

    if (original.getReschedulingHistory() == null) {
      original.setReschedulingHistory(new java.util.ArrayList<>());
    }
    original.getReschedulingHistory().add(record);
    visitRepository.save(original);
    log.info(
        "[RescheduleService] Rescheduling record appended to original visit '{}'", originalVisitId);

    return RescheduleResponse.from(
        newVisit, "Visit rescheduled successfully. New visit ID: " + newVisit.getId());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // GET RESCHEDULED VISITS FROM ORIGINAL
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns all visits that were created by rescheduling the given original visit. Used by the
   * frontend to render the "View rescheduled visit" link.
   *
   * @param originVisitId The ID of the original cancelled visit
   */
  public List<Visit> getRescheduledVisits(String originVisitId) {
    return visitRepository.findByOriginVisitId(originVisitId);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Private helpers
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Validates that the agent has no SCHEDULED visit within the conflict window. Throws 422 if
   * unavailable.
   *
   * @param agentId Agent to check
   * @param dateTime Proposed datetime
   * @param excludeId Visit ID to exclude from the check (null = no exclusion)
   */
  private void validateAgentAvailability(String agentId, Instant dateTime, String excludeId) {
    Instant windowStart = dateTime.minus(AVAILABILITY_BUFFER_MINUTES, ChronoUnit.MINUTES);
    Instant windowEnd = dateTime.plus(AVAILABILITY_BUFFER_MINUTES, ChronoUnit.MINUTES);

    boolean conflict =
        visitRepository.existsByAgentIdAndStartTimeBetweenAndStatus(
            agentId, windowStart, windowEnd, EventStatus.SCHEDULED);

    if (conflict) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "The agent already has a scheduled visit within 1 hour of the proposed time. "
              + "Please choose a different time slot.");
    }
  }

  /**
   * Validates that the property has no SCHEDULED visit within the conflict window. Throws 422 if
   * unavailable.
   */
  private void validatePropertyAvailability(String propertyId, Instant dateTime, String excludeId) {
    Instant windowStart = dateTime.minus(AVAILABILITY_BUFFER_MINUTES, ChronoUnit.MINUTES);
    Instant windowEnd = dateTime.plus(AVAILABILITY_BUFFER_MINUTES, ChronoUnit.MINUTES);

    boolean conflict =
        visitRepository.existsByPropertyIdAndStartTimeBetweenAndStatus(
            propertyId, windowStart, windowEnd, EventStatus.SCHEDULED);

    if (conflict) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "The property already has a scheduled visit within 1 hour of the proposed time. "
              + "Please choose a different time slot.");
    }
  }

  /**
   * Builds the new Visit entity from the original, applying the new datetime. Preserves all details
   * from the original visit (propertyId, clientId, agentId, propertyName, agentName, clientName,
   * type) (PA2). Sets: originVisitId to link back to the original (PA2), status to SCHEDULED, and
   * createdAt to now.
   *
   * <p>[CalendarEventRepository] This method creates calendar events that will be persisted via
   * {@link VisitRepository#save(Visit)}. The resulting Visit is a calendar event with:
   *
   * <ul>
   *   <li>EventStatus.SCHEDULED status
   *   <li>originVisitId linking to the cancelled event
   *   <li>Agent, property, and client context preserved
   *   <li>New start/end times for the rescheduled slot
   * </ul>
   *
   * @param original The cancelled Visit being rescheduled
   * @param request New datetime details (newStartTime, newEndTime, notes)
   * @param agentId Agent performing the reschedule action
   * @return New Visit entity ready for persistence
   */
  private Visit buildNewVisit(Visit original, RescheduleRequest request, String agentId) {
    Visit newVisit = new Visit();
    // Copy all relevant fields from original
    newVisit.setPropertyId(original.getPropertyId());
    newVisit.setPropertyName(original.getPropertyName());
    newVisit.setPropertyAddress(original.getPropertyAddress());
    newVisit.setAgentId(original.getAgentId());
    newVisit.setAgentName(original.getAgentName());
    newVisit.setClientId(original.getClientId());
    newVisit.setClientName(original.getClientName());
    newVisit.setStartTime(request.getNewStartTime());
    newVisit.setEndTime(request.getNewEndTime());
    newVisit.setVehicleId(original.getVehicleId());
    newVisit.setTravelTimeGo(original.getTravelTimeGo());
    newVisit.setTravelTimeBack(original.getTravelTimeBack());
    newVisit.setType(original.getType());
    newVisit.setStatus(EventStatus.SCHEDULED);
    newVisit.setNotes(request.getNotes() != null ? request.getNotes() : original.getNotes());
    newVisit.setOriginVisitId(original.getId());
    newVisit.setCreatedAt(Instant.now());
    newVisit.setReschedulingHistory(new java.util.ArrayList<>());
    newVisit.setOwnEvent(original.getOwnEvent());
    return newVisit;
  }

  /**
   * Converts a Visit entity to a CalendarEvent for storage in the calendar repository. Maps all
   * relevant fields from the Visit to create a corresponding calendar event entry.
   *
   * @param visit The Visit to convert
   * @return CalendarEvent entity ready for persistence in CalendarEventRepository
   */
  private CalendarEvent convertVisitToCalendarEvent(Visit visit) {
    return CalendarEvent.builder()
        .id(visit.getId())
        .propertyId(visit.getPropertyId())
        .propertyName(visit.getPropertyName())
        .propertyAddress(visit.getPropertyAddress())
        .agentId(visit.getAgentId())
        .agentName(visit.getAgentName())
        .clientId(visit.getClientId())
        .clientName(visit.getClientName())
        .vehicleId(visit.getVehicleId())
        .travelTimeGo(visit.getTravelTimeGo())
        .travelTimeBack(visit.getTravelTimeBack())
        .startTime(visit.getStartTime())
        .endTime(visit.getEndTime())
        .type(CalendarEvent.EventType.valueOf(visit.getType().name()))
        .status(CalendarEvent.EventStatus.valueOf(visit.getStatus().name()))
        .notes(visit.getNotes())
        .createdAt(visit.getCreatedAt())
        .build();
  }
}

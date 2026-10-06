package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.RegistrarResultadoRequest;
import com.inmobiliaria.visit_calendar_service.dto.RescheduleRequest;
import com.inmobiliaria.visit_calendar_service.dto.RescheduleResponse;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.CalendarResponse;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.ConflictResponse;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.CreateVisitRequest;
import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.dto.response.VisitHistoryResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.VisitResponse;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.service.CalendarService;
import com.inmobiliaria.visit_calendar_service.service.RescheduleService;
import com.inmobiliaria.visit_calendar_service.service.VehicleService;
import com.inmobiliaria.visit_calendar_service.service.VisitRequestService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controlador REST para el calendario compartido y la programación de visitas.
 *
 * <p>HU1: GET /calendar → Visualizar calendario del equipo HU2: POST /visits → Programar una visita
 * GET /visits/conflict-check → Verificar conflicto antes de crear GET /visits/agenda/{day} → Agenda
 * del día de un agente (PA3 HU2) PATCH /visits/{id}/cancel → Cancelar una visita GET /visits/{id} →
 * Detalle de un evento
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class CalendarController {

  private final CalendarService calendarService;
  private final ResponseFactory responseFactory;
  private final RescheduleService rescheduleService;
  private final VehicleService vehicleService;
  private final VisitRequestService visitRequestService;

  @PatchMapping(value = {"/visits/{id}/resultado", "/visits/{id}/result"})
  public ResponseEntity<ApiResponse<VisitResponse>> registrarResultado(
      @PathVariable String id,
      @Valid @RequestBody RegistrarResultadoRequest request,
      @RequestHeader("X-Agent-Id") String agentId) {
    Visit updated = visitRequestService.registrarResultado(id, request, agentId);
    return ResponseEntity.ok(responseFactory.success("Resultado registrado", toResponse(updated)));
  }

  private VisitResponse toResponse(Visit visit) {
    return new VisitResponse(
        visit.getId(),
        visit.getPropertyId(),
        visit.getPropertyName(),
        visit.getClientId(),
        visit.getClientName(),
        visit.getAgentId(),
        visit.getAgentName(),
        visit.getStartTime(),
        visit.getEndTime(),
        visit.getStatus(),
        visit.getResultado(),
        visit.getObservaciones(),
        visit.getFechaRegistroResultado());
  }

  // -----------------------------------------------------------------------
  // HU1: Visualizar calendario compartido del equipo
  // -----------------------------------------------------------------------

  /**
   * GET /calendar
   *
   * <p>Devuelve el calendario del equipo en el rango de fechas indicado. Parámetros opcionales de
   * filtro: agentId, propertyId. El header X-Agent-Id identifica al agente autenticado para marcar
   * sus propios eventos como ownEvent=true (diferenciación visual, PA1).
   *
   * <p>PA1: ver visitas de todo el equipo, propias destacadas PA3: filtrar por propiedad → solo
   * eventos de ese inmueble
   *
   * <p>Ejemplos: GET /calendar?from=2025-06-01T00:00:00&to=2025-06-07T23:59:59 GET
   * /calendar?propertyId=abc123&from=...&to=... GET /calendar?agentId=xyz&from=...&to=...
   */
  @GetMapping("/calendar")
  public ResponseEntity<ApiResponse<CalendarResponse>> getCalendar(
      @RequestHeader(value = "X-Agent-Id", required = false) String requestingAgentId,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String propertyId) {

    log.debug("GET /calendar: agente={}, desde={}, hasta={}", requestingAgentId, from, to);

    CalendarResponse response =
        calendarService.getCalendar(requestingAgentId, from, to, agentId, propertyId);

    return ResponseEntity.ok(
        responseFactory.success("Calendario obtenido correctamente", response));
  }

  // -----------------------------------------------------------------------
  // HU2: Programar visita
  // -----------------------------------------------------------------------

  /**
   * POST /visits
   *
   * <p>Programa una nueva visita. Valida que el inmueble no tenga conflicto de horario (PA2). Al
   * confirmarse, aparece en el calendario compartido (PA1).
   *
   * <p>Body: CreateVisitRequest Response 201: Visit (la visita creada) Response 409:
   * ConflictResponse (si hay conflicto de horario)
   */
  @PostMapping("/visits")
  public ResponseEntity<ApiResponse<Visit>> createVisit(
      @Valid @RequestBody CreateVisitRequest request) {

    log.debug(
        "POST /visits: propiedad={}, agente={}, inicio={}",
        request.getPropertyId(),
        request.getAgentId(),
        request.getStartTime());

    Visit created = calendarService.createVisit(request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseFactory.created("Visita programada exitosamente", created));
  }

  /**
   * GET /visits/conflict-check
   *
   * <p>Verifica si existe conflicto de horario ANTES de enviar el formulario. Permite al frontend
   * alertar al usuario (PA2 HU1 + PA2 HU2) sin crear el evento.
   *
   * <p>Params: propertyId, startTime, endTime
   */
  @GetMapping("/visits/conflict-check")
  public ResponseEntity<ApiResponse<ConflictResponse>> checkConflict(
      @RequestParam String propertyId,
      @RequestParam(required = false) String agentId,
      @RequestParam Instant startTime,
      @RequestParam Instant endTime) {

    ConflictResponse result =
        calendarService.checkConflict(propertyId, agentId, startTime, endTime);

    return ResponseEntity.ok(
        responseFactory.success(
            result.isHasConflict() ? "Conflicto detectado" : "Horario disponible", result));
  }

  /**
   * GET /visits/agenda
   *
   * <p>Agenda del día de un agente específico (PA3 HU2). Param: agentId, day (ISO datetime del día
   * a consultar)
   */
  @GetMapping("/visits/agenda")
  public ResponseEntity<ApiResponse<List<Visit>>> getDayAgenda(
      @RequestParam String agentId, @RequestParam Instant day) {

    List<Visit> agenda = calendarService.getAgentDayAgenda(agentId, day);

    return ResponseEntity.ok(responseFactory.success("Agenda obtenida correctamente", agenda));
  }

  /** GET /visits/{id} Detalle de un evento de visita. */
  @GetMapping("/visits/{id}")
  public ResponseEntity<ApiResponse<Visit>> getVisitById(
      @PathVariable String id,
      @RequestHeader(value = "X-Agent-Id", required = false) String agentId) {

    Visit event = calendarService.getById(id, agentId);
    return ResponseEntity.ok(responseFactory.success("Evento encontrado", event));
  }

  /** GET /visits/property/{propertyId} Devuelve el historial de visitas de un inmueble. */
  @GetMapping("/visits/property/{propertyId}")
  public ResponseEntity<ApiResponse<List<Visit>>> getVisitsByProperty(
      @PathVariable String propertyId) {
    List<Visit> visits = calendarService.getVisitsByProperty(propertyId);
    return ResponseEntity.ok(responseFactory.success("Historial de visitas obtenido", visits));
  }

  /**
   * GET /properties/{id}/visits/history Devuelve el historial de visitas completadas de una
   * propiedad con estadísticas (conteo y porcentaje de visitas interesadas).
   *
   * <p>Parámetros opcionales: - date_since: Filtrar desde esta fecha (ISO Instant) - date_until:
   * Filtrar hasta esta fecha (ISO Instant) - page: Número de página (0-based, default: 0) -
   * page_size: Tamaño de página (10, 20 o 30, default: 10)
   *
   * <p>Response incluye: - Lista paginada de visitas realizadas - Conteo de visitas interesadas -
   * Porcentaje de visitas interesadas - Información de paginación - Mensaje descriptivo si no hay
   * visitas
   */
  @GetMapping("/properties/{id}/visits/history")
  public ResponseEntity<ApiResponse<VisitHistoryResponse>> getVisitHistory(
      @PathVariable String id,
      @RequestParam(required = false) Instant date_since,
      @RequestParam(required = false) Instant date_until,
      @RequestParam(name = "page", defaultValue = "0") Integer pageNumber,
      @RequestParam(name = "page_size", defaultValue = "10") Integer pageSize) {

    log.debug(
        "GET /properties/{}/visits/history: desde={}, hasta={}, página={}, tamaño={}",
        id,
        date_since,
        date_until,
        pageNumber,
        pageSize);

    // Validar que pageSize sea uno de los valores permitidos
    if (pageSize != 10 && pageSize != 20 && pageSize != 30) {
      return ResponseEntity.badRequest()
          .body(
              responseFactory.validationError(
                  "Parámetro inválido: page_size debe ser 10, 20 o 30",
                  "page_size",
                  "INVALID_PAGE_SIZE",
                  "El tamaño de página debe ser 10, 20 o 30 elementos"));
    }

    VisitHistoryResponse history =
        calendarService.getVisitHistory(id, date_since, date_until, pageNumber, pageSize);

    String successMessage =
        history.getVisits().isEmpty()
            ? history.getMessage()
            : String.format(
                "Historial de visitas obtenido correctamente (página %d de %d)",
                pageNumber + 1, history.getTotalPages());

    return ResponseEntity.ok(responseFactory.success(successMessage, history));
  }

  /** PATCH /visits/{id}/cancel Cancela una visita (solo el agente dueño). */
  @PatchMapping("/visits/{id}/cancel")
  public ResponseEntity<ApiResponse<Visit>> cancelVisit(
      @PathVariable String id, @RequestHeader("X-Agent-Id") String agentId) {

    Visit cancelled = calendarService.cancelEvent(id, agentId);
    return ResponseEntity.ok(responseFactory.success("Visita cancelada", cancelled));
  }

  // -----------------------------------------------------------------------
  // Endpoints de vehículos
  // -----------------------------------------------------------------------

  /**
   * GET /visits/{visitId}/vehicle-assignment Recupera los detalles de asignación de vehículo y
   * tránsito de una visita (evento).
   */
  @GetMapping("/visits/{visitId}/vehicle-assignment")
  public ResponseEntity<ApiResponse<Visit>> getVehicleAssignment(@PathVariable String visitId) {
    Visit event = vehicleService.getVisitWithAssignment(visitId);
    return ResponseEntity.ok(responseFactory.success("Asignación de vehículo recuperada", event));
  }

  /**
   * POST /visits/{visitId}/vehicle Asigna un vehículo a una visita con tiempos flexibles de
   * desplazamiento.
   */
  @PostMapping("/visits/{visitId}/vehicle")
  public ResponseEntity<ApiResponse<Visit>> assignVehicle(
      @PathVariable String visitId, @RequestBody VehicleAssignmentRequest request) {

    Visit updatedEvent =
        vehicleService.assignVehicleToVisit(
            visitId,
            request.getVehicleId(),
            request.getTravelTimeGo(),
            request.getTravelTimeBack());
    return ResponseEntity.ok(
        responseFactory.success("Vehículo asignado a la visita exitosamente", updatedEvent));
  }

  // -----------------------------------------------------------------------
  // Endpoints de reprogramación (desde Sprint3_DEV)
  // -----------------------------------------------------------------------

  /**
   * Reschedules a cancelled visit by creating a new SCHEDULED visit.
   *
   * <p>Returns 201 with the new visit data on success. Returns 404 if the original visit does not
   * exist. Returns 409 if the visit is not in CANCELLED status (PA3). Returns 422 if the agent or
   * property is unavailable at the new time.
   *
   * <p>API: POST /visits/{id}/reschedule Body: { "newDateTime": "2025-07-10T10:00:00", "notes":
   * "..." }
   */
  @PostMapping("/visits/{id}/reschedule")
  public ResponseEntity<?> rescheduleVisit(
      @PathVariable String id,
      @RequestHeader("X-Agent-Id") String agentId,
      @Valid @RequestBody RescheduleRequest request) {

    try {
      RescheduleResponse response = rescheduleService.reschedule(id, agentId, request);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(responseFactory.created("Visita reprogramada exitosamente", response));
    } catch (ResponseStatusException e) {
      return ResponseEntity.status(e.getStatusCode())
          .body(responseFactory.error(e.getReason() != null ? e.getReason() : e.getMessage()));
    }
  }

  /**
   * Returns all visits created by rescheduling the given original visit. Used by the frontend to
   * render the "View rescheduled visit" link.
   *
   * <p>API: GET /visits/{id}/rescheduled
   */
  @GetMapping("/visits/{id}/rescheduled")
  public ResponseEntity<List<Visit>> getRescheduledVisits(@PathVariable String id) {
    List<Visit> rescheduled = rescheduleService.getRescheduledVisits(id);
    return ResponseEntity.ok(rescheduled);
  }

  // -----------------------------------------------------------------------
  // Clase interna para request de vehículo
  // -----------------------------------------------------------------------
  @lombok.Data
  public static class VehicleAssignmentRequest {
    private String vehicleId;
    private Integer travelTimeGo;
    private Integer travelTimeBack;
  }
}

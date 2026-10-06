package com.inmobiliaria.visit_calendar_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.visit_calendar_service.client.PersonClient;
import com.inmobiliaria.visit_calendar_service.client.PropertyClient;
import com.inmobiliaria.visit_calendar_service.domain.InteractionType;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.CalendarResponse;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.ConflictResponse;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.CreateVisitRequest;
import com.inmobiliaria.visit_calendar_service.dto.response.PersonResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.PropertyResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.VisitHistoryResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.VisitResponse;
import com.inmobiliaria.visit_calendar_service.exception.ResourceNotFoundException;
import com.inmobiliaria.visit_calendar_service.exception.ScheduleConflictException;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio de lógica de negocio para el calendario de visitas.
 *
 * <p>HU1: Visualizar calendario compartido con filtros (agente, fecha, propiedad). HU2: Programar
 * visita validando disponibilidad y detectando conflictos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

  private final CalendarEventRepository calendarEventRepository;
  private final PropertyClient propertyClient;
  private final NotificationService notificationService;
  private final PersonClient personClient;
  private final VisitRepository visitRepository;
  private final ObjectMapper objectMapper;
  private final AgentAvailabilityService agentAvailabilityService;

  // =====================================================================
  // HU1: GET /calendar — Visualizar calendario compartido del equipo
  // =====================================================================

  /**
   * PA1: Retorna todos los eventos del calendario en un rango de fechas. Los eventos del agente
   * autenticado se marcan con ownEvent=true (para resaltarlos visualmente). PA3: Si se pasa
   * propertyId, filtra solo por esa propiedad.
   *
   * @param requestingAgentId ID del agente autenticado (para marcar sus propios eventos)
   * @param from Inicio del rango de fechas
   * @param to Fin del rango de fechas
   * @param agentId Filtro opcional por agente específico
   * @param propertyId Filtro opcional por propiedad específica
   */
  public CalendarResponse getCalendar(
      String requestingAgentId, Instant from, Instant to, String agentId, String propertyId) {

    log.debug(
        "Obteniendo calendario: agenteFiltro={}, propiedadFiltro={}, desde={}, hasta={}",
        agentId,
        propertyId,
        from,
        to);

    List<CalendarEvent> events;

    if (propertyId != null && !propertyId.isBlank()) {
      // PA3: Filtro por propiedad específica
      events = calendarEventRepository.findByPropertyIdAndDateRange(propertyId, from, to);
    } else if (agentId != null && !agentId.isBlank()) {
      // Filtro por agente específico
      events = calendarEventRepository.findByAgentIdAndDateRange(agentId, from, to);
    } else {
      // Vista completa del equipo
      events = calendarEventRepository.findByDateRange(from, to);
    }

    List<Visit> responses =
        events.stream()
            .map(event -> toResponse(event, requestingAgentId))
            .collect(Collectors.toList());

    long myEventsCount = responses.stream().filter(v -> v.getOwnEvent()).count();

    return CalendarResponse.builder()
        .events(responses)
        .from(from)
        .to(to)
        .totalEvents(responses.size())
        .myEvents((int) myEventsCount)
        .build();
  }

  // =====================================================================
  // HU2: POST /visits — Programar visita con validación de conflictos
  // =====================================================================

  /**
   * PA2: Valida si existe conflicto de horario ANTES de crear el evento. Retorna detalles del
   * conflicto y una sugerencia de horario alternativo.
   */
  public ConflictResponse checkConflict(
      String propertyId, String agentId, Instant startTime, Instant endTime) {
    validateDateRange(startTime, endTime);

    // 1. Verificar disponibilidad del agente primero (Sprint 5)
    if (agentId != null && !agentId.isBlank()) {
      try {
        agentAvailabilityService.checkAgentAvailability(agentId, startTime, endTime);
      } catch (ScheduleConflictException ex) {
        return ConflictResponse.builder()
            .hasConflict(true)
            .message(ex.getMessage())
            .conflictingEvents(List.of())
            .build();
      }

      // 1b. Check if agent is busy (has conflicting scheduled visits)
      List<CalendarEvent> agentConflicts =
          calendarEventRepository.findConflictingEventsForAgent(agentId, startTime, endTime);
      if (!agentConflicts.isEmpty()) {
        return ConflictResponse.builder()
            .hasConflict(true)
            .message("El agente ya tiene otra visita programada en este horario.")
            .conflictingEvents(
                agentConflicts.stream().map(e -> toResponse(e, null)).collect(Collectors.toList()))
            .build();
      }
    }

    // 2. Verificar conflictos del inmueble
    List<CalendarEvent> conflicts =
        calendarEventRepository.findConflictingEventsForNew(propertyId, startTime, endTime);

    if (conflicts.isEmpty()) {
      return ConflictResponse.builder()
          .hasConflict(false)
          .message("El horario está disponible")
          .conflictingEvents(List.of())
          .build();
    }

    // Sugerir horario después del último evento conflictivo
    Instant suggestedStart =
        conflicts.stream()
            .map(CalendarEvent::getEndTime)
            .max(Instant::compareTo)
            .orElse(endTime)
            .plus(30, ChronoUnit.MINUTES);

    long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
    Instant suggestedEnd = suggestedStart.plus(durationMinutes, ChronoUnit.MINUTES);

    return ConflictResponse.builder()
        .hasConflict(true)
        .message(
            "Ya existe una visita programada para este inmueble en ese horario. "
                + "Por favor selecciona otro horario.")
        .conflictingEvents(
            conflicts.stream().map(e -> toResponse(e, null)).collect(Collectors.toList()))
        .suggestedStartTime(suggestedStart)
        .suggestedEndTime(suggestedEnd)
        .build();
  }

  /**
   * PA1 + PA2 + PA3 de HU2: Crea un nuevo evento de visita en el calendario. Lanza
   * ScheduleConflictException si ya existe un conflicto de horario. La visita aparece
   * automáticamente en el calendario compartido (PA1).
   */
  public Visit createVisit(CreateVisitRequest request) {
    validateDateRange(request.getStartTime(), request.getEndTime());

    // 1. Validar disponibilidad del agente (Sprint 5)
    if (request.getAgentId() != null && !request.getAgentId().isBlank()) {
      agentAvailabilityService.checkAgentAvailability(
          request.getAgentId(), request.getStartTime(), request.getEndTime());

      // Check if agent is busy
      List<CalendarEvent> agentConflicts =
          calendarEventRepository.findConflictingEventsForAgent(
              request.getAgentId(), request.getStartTime(), request.getEndTime());
      if (!agentConflicts.isEmpty()) {
        throw new ScheduleConflictException(
            "El agente "
                + request.getAgentName()
                + " ya tiene otra visita programada en ese horario.");
      }
    }

    // Verificar conflictos de horario (PA2)
    List<CalendarEvent> conflicts =
        calendarEventRepository.findConflictingEventsForNew(
            request.getPropertyId(), request.getStartTime(), request.getEndTime());

    if (!conflicts.isEmpty()) {
      throw new ScheduleConflictException(
          "Ya existe una visita programada para el inmueble '"
              + request.getPropertyName()
              + "' en ese horario. "
              + "Por favor selecciona otro horario.");
    }

    PropertyResponse property = propertyClient.getPropertyById(request.getPropertyId());
    if (property != null && property.ownerId() != null) {
      PersonResponse owner = personClient.getPersonByAuthUserId(property.ownerId());
      if (owner != null && owner.email() != null) {
        notificationService.notifyPropertyOwner(owner.email(), owner.fullName(), request);
      }
    }

    CalendarEvent event =
        CalendarEvent.builder()
            .propertyId(request.getPropertyId())
            .propertyName(request.getPropertyName())
            .propertyAddress(request.getPropertyAddress())
            .agentId(request.getAgentId())
            .agentName(request.getAgentName())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .type(CalendarEvent.EventType.VISIT)
            .status(CalendarEvent.EventStatus.SCHEDULED)
            .notes(request.getNotes())
            .createdAt(Instant.now())
            .build();

    CalendarEvent saved = calendarEventRepository.save(event);
    log.info(
        "Visita creada exitosamente: id={}, propiedad={}, agente={}, inicio={}",
        saved.getId(),
        saved.getPropertyName(),
        saved.getAgentName(),
        saved.getStartTime());

    notifyOwnerAboutVisit(saved, "schedule");

    return toResponse(saved, request.getAgentId());
  }

  /** PA3 de HU2: Obtiene la agenda del día para un agente específico. */
  public List<Visit> getAgentDayAgenda(String agentId, Instant day) {
    Instant dayStart = day.truncatedTo(ChronoUnit.DAYS);
    Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.NANOS);
    List<CalendarEvent> events =
        calendarEventRepository.findByDayAndAgent(dayStart, dayEnd, agentId);
    return events.stream().map(e -> toResponse(e, agentId)).collect(Collectors.toList());
  }

  /**
   * Obtiene todas las visitas de una propiedad específica. Usado por el frontend para mostrar el
   * historial de visitas en el detalle del inmueble.
   */
  public List<Visit> getVisitsByProperty(String propertyId) {
    log.debug("Obteniendo historial de visitas para la propiedad: {}", propertyId);
    return visitRepository.findByPropertyId(propertyId);
  }

  /**
   * HU de historial de visitas: Obtiene el historial de visitas completadas de una propiedad con
   * estadísticas (conteo y porcentaje de "INTERESADO").
   *
   * <p>PA1: Retorna lista cronológica (más recientes primero) con fecha, hora y resultado. PA2:
   * Mensaje descriptivo si no hay visitas. PA3: Paginación de 10, 20 o 30 elementos.
   *
   * @param propertyId ID de la propiedad
   * @param dateSince Fecha desde (opcional)
   * @param dateUntil Fecha hasta (opcional)
   * @param pageNumber Número de página (0-based)
   * @param pageSize Tamaño de página (10, 20 o 30)
   * @return VisitHistoryResponse con visitas completadas y estadísticas
   */
  public VisitHistoryResponse getVisitHistory(
      String propertyId,
      Instant dateSince,
      Instant dateUntil,
      Integer pageNumber,
      Integer pageSize) {

    log.debug(
        "Obteniendo historial de visitas para propiedad: {}, desde: {}, hasta: {}, "
            + "página: {}, tamaño: {}",
        propertyId,
        dateSince,
        dateUntil,
        pageNumber,
        pageSize);

    // Validar parámetros de paginación
    if (pageNumber == null || pageNumber < 0) {
      pageNumber = 0;
    }
    if (pageSize == null || (pageSize != 10 && pageSize != 20 && pageSize != 30)) {
      pageSize = 10; // Default
    }

    // Obtener todas las visitas completadas con filtro de fechas si es necesario
    List<CalendarEvent> allCompletedVisits;
    long totalInterestedCount;
    long totalVisitCount;

    if (dateSince != null && dateUntil != null) {
      allCompletedVisits =
          calendarEventRepository.findCompletedVisitsByPropertyAndDateRange(
              propertyId, dateSince, dateUntil);
      totalInterestedCount =
          calendarEventRepository.countCompletedInterestedByPropertyAndDateRange(
              propertyId, dateSince, dateUntil);
      totalVisitCount =
          calendarEventRepository.countByPropertyIdAndStatus(
              propertyId, CalendarEvent.EventStatus.COMPLETED);
    } else {
      allCompletedVisits = calendarEventRepository.findCompletedVisitsByProperty(propertyId);
      totalInterestedCount = calendarEventRepository.countCompletedInterestedByProperty(propertyId);
      totalVisitCount =
          calendarEventRepository.countByPropertyIdAndStatus(
              propertyId, CalendarEvent.EventStatus.COMPLETED);
    }

    // Ordenar por fecha descendente (más recientes primero)
    allCompletedVisits.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));

    // Si no hay visitas, retornar respuesta con mensaje descriptivo
    if (allCompletedVisits.isEmpty()) {
      VisitHistoryResponse response =
          VisitHistoryResponse.builder()
              .visits(List.of())
              .totalVisits(0L)
              .interestedCount(0L)
              .interestedPercentage(
                  java.math.BigDecimal.ZERO) // Usar BigDecimal para mantener precisión
              .pageNumber(pageNumber)
              .pageSize(pageSize)
              .totalPages(0)
              .message("Aún no se ha agendado ninguna visita para esta propiedad")
              .build();
      return response;
    }

    // Calcular porcentaje de visitas interesadas
    java.math.BigDecimal interestedPercentage =
        totalVisitCount > 0
            ? java.math.BigDecimal.valueOf(totalInterestedCount)
                .divide(
                    java.math.BigDecimal.valueOf(totalVisitCount),
                    2,
                    java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(100))
            : java.math.BigDecimal.ZERO;

    // Aplicar paginación
    int totalPages = (int) Math.ceil((double) allCompletedVisits.size() / pageSize);
    int startIndex = pageNumber * pageSize;
    int endIndex = Math.min(startIndex + pageSize, allCompletedVisits.size());

    List<VisitResponse> pagedVisits;
    if (startIndex >= allCompletedVisits.size()) {
      pagedVisits = List.of();
    } else {
      pagedVisits =
          allCompletedVisits.subList(startIndex, endIndex).stream()
              .map(this::toVisitResponse)
              .collect(Collectors.toList());
    }

    VisitHistoryResponse response =
        VisitHistoryResponse.builder()
            .visits(pagedVisits)
            .totalVisits(totalVisitCount)
            .interestedCount(totalInterestedCount)
            .interestedPercentage(interestedPercentage)
            .pageNumber(pageNumber)
            .pageSize(pageSize)
            .totalPages(totalPages)
            .message(
                String.format(
                    "Historial de %d visitas realizadas obtenido correctamente",
                    allCompletedVisits.size()))
            .build();

    return response;
  }

  /** Obtiene un evento por ID. */
  public Visit getById(String id, String requestingAgentId) {
    CalendarEvent event =
        calendarEventRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado: " + id));
    return toResponse(event, requestingAgentId);
  }

  /** Cancela un evento (solo el agente dueño puede cancelar el suyo). */
  public Visit cancelEvent(String id, String agentId) {
    CalendarEvent event =
        calendarEventRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado: " + id));

    if (!event.getAgentId().equals(agentId)) {
      throw new IllegalArgumentException("Solo el agente responsable puede cancelar esta visita");
    }

    event.setStatus(CalendarEvent.EventStatus.CANCELLED);
    CalendarEvent saved = calendarEventRepository.save(event);
    log.info("Visita cancelada: id={}", id);

    notifyOwnerAboutVisit(saved, "cancel");

    return toResponse(saved, agentId);
  }

  // =====================================================================
  // Helpers
  // =====================================================================

  private void notifyOwnerAboutVisit(CalendarEvent event, String action) {
    try {
      String raw = propertyClient.getPropertyRaw(event.getPropertyId());
      JsonNode root = objectMapper.readTree(raw);
      JsonNode dataNode = root.get("data");
      if (dataNode == null) {
        log.warn("No data node in response for property {}", event.getPropertyId());
        return;
      }
      PropertyResponse property = objectMapper.treeToValue(dataNode, PropertyResponse.class);
      if (property == null || property.ownerId() == null) {
        log.warn("Property {} has no owner", event.getPropertyId());
        return;
      }
      if (property == null || property.ownerId() == null) {
        log.debug("Property or owner not found for notification: {}", event.getPropertyId());
        return;
      }
      String ownerId = property.ownerId();
      // Obtener nombre del propietario
      PersonResponse owner = personClient.getPersonByAuthUserId(ownerId);
      String ownerName =
          (owner != null && owner.fullName() != null) ? owner.fullName() : "Propietario";

      String subject;
      String content;
      InteractionType type;
      Map<String, Object> details =
          Map.of(
              "propertyId", event.getPropertyId(),
              "propertyName", event.getPropertyName(),
              "visitId", event.getId(),
              "visitStartTime", event.getStartTime().toString(),
              "agentName", event.getAgentName());

      if ("schedule".equals(action)) {
        subject = "Nueva visita agendada para tu propiedad";
        content =
            String.format(
                "El agente %s ha agendado una visita para tu propiedad '%s' el día %s.",
                event.getAgentName(), event.getPropertyName(), event.getStartTime());
        type = InteractionType.PROPIEDAD_MOD;
      } else {
        subject = "Visita cancelada para tu propiedad";
        content =
            String.format(
                "El agente %s ha cancelado la visita programada para tu propiedad '%s' el día %s.",
                event.getAgentName(), event.getPropertyName(), event.getStartTime());
        type = InteractionType.PROPIEDAD_MOD;
      }

      notificationService.sendInAppNotificationToOwner(
          ownerId, ownerName, event.getPropertyName(), subject, content, type, details);
    } catch (Exception e) {
      log.warn(
          "Could not send in-app notification to owner for visit {}: {}",
          event.getId(),
          e.getMessage());
    }
  }

  private void validateDateRange(Instant start, Instant end) {
    if (start == null || end == null) {
      throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias");
    }
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
    }
    if (start.isBefore(Instant.now())) {
      throw new IllegalArgumentException("No se puede programar una visita en el pasado");
    }
  }

  /** Convierte un Visit a VisitResponse (record DTO). */
  private VisitResponse toVisitResponse(CalendarEvent event) {
    Visit.ResultadoVisita resultado = null;
    if (event.getResultado() != null) {
      try {
        resultado = Visit.ResultadoVisita.valueOf(event.getResultado());
      } catch (IllegalArgumentException e) {
        // Ignorar si el resultado no coincide con el enum
      }
    }

    return new VisitResponse(
        event.getId(),
        event.getPropertyId(),
        event.getPropertyName(),
        event.getClientId(),
        event.getClientName(),
        event.getAgentId(),
        event.getAgentName(),
        event.getStartTime(),
        event.getEndTime(),
        Visit.EventStatus.valueOf(event.getStatus().name()),
        resultado,
        event.getObservaciones(),
        event.getFechaRegistroResultado());
  }

  private Visit toResponse(CalendarEvent event, String requestingAgentId) {
    Visit visit = new Visit();
    visit.setId(event.getId());
    visit.setPropertyId(event.getPropertyId());
    visit.setPropertyName(event.getPropertyName());
    visit.setPropertyAddress(event.getPropertyAddress());
    visit.setAgentId(event.getAgentId());
    visit.setAgentName(event.getAgentName());
    visit.setVehicleId(event.getVehicleId());
    visit.setTravelTimeGo(event.getTravelTimeGo());
    visit.setTravelTimeBack(event.getTravelTimeBack());
    visit.setStartTime(event.getStartTime());
    visit.setEndTime(event.getEndTime());
    visit.setType(Visit.EventType.valueOf(event.getType().name()));
    visit.setStatus(Visit.EventStatus.valueOf(event.getStatus().name()));
    visit.setNotes(event.getNotes());
    visit.setCreatedAt(event.getCreatedAt());
    visit.setClientId(event.getClientId());
    visit.setClientName(event.getClientName());

    if (event.getResultado() != null) {
      try {
        visit.setResultado(Visit.ResultadoVisita.valueOf(event.getResultado()));
      } catch (IllegalArgumentException e) {
        // Ignorar si el resultado no coincide con el enum
      }
    }
    visit.setObservaciones(event.getObservaciones());
    visit.setFechaRegistroResultado(event.getFechaRegistroResultado());

    // PA1 de HU1: marca visualmente los eventos del agente autenticado
    visit.setOwnEvent(requestingAgentId != null && requestingAgentId.equals(event.getAgentId()));
    // Inicializar históricos vacíos
    visit.setReassignmentHistory(new ArrayList<>());
    visit.setReschedulingHistory(new ArrayList<>());
    visit.setOriginVisitId(null);
    visitRepository.save(visit);
    return visit;
  }
}

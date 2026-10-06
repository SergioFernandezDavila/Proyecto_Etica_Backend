package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.client.PersonClient;
import com.inmobiliaria.visit_calendar_service.client.UserClient;
import com.inmobiliaria.visit_calendar_service.dto.ReassignmentRequestRequestDTO;
import com.inmobiliaria.visit_calendar_service.dto.ReassignmentRequestResponseDTO;
import com.inmobiliaria.visit_calendar_service.dto.RequestResponseDTO;
import com.inmobiliaria.visit_calendar_service.dto.response.PersonResponse;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.ReassignmentHistory;
import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import com.inmobiliaria.visit_calendar_service.repository.ReassignmentRequestRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Servicio que contiene toda la lógica de negocio para la reasignación de citas. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReassignmentService {

  private final ReassignmentRequestRepository reassignmentRequestRepository;
  private final CalendarEventRepository calendarEventRepository;
  private final VisitRepository visitRepository;
  private final ReassignmentNotificationService reassignmentNotificationService;
  private final UserClient userClient;
  private final PersonClient personClient;
  private final AgentAvailabilityService agentAvailabilityService;

  public ReassignmentRequestResponseDTO requestReassignment(
      String visitId, String requestingAgentId, ReassignmentRequestRequestDTO dto) {
    CalendarEvent event =
        calendarEventRepository
            .findById(visitId)
            .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + visitId));

    if (!event.getAgentId().equals(requestingAgentId)) {
      throw new RuntimeException("No tienes permiso para reasignar esta cita: no te pertenece.");
    }

    List<ReassignmentRequest> pending =
        reassignmentRequestRepository.findByVisitIdAndStatus(
            visitId, ReassignmentRequest.RequestStatus.PENDING);
    if (!pending.isEmpty()) {
      throw new RuntimeException(
          "Ya existe una solicitud de reasignación pendiente para esta cita.");
    }

    if (requestingAgentId.equals(dto.getDestinationAgentId())) {
      throw new RuntimeException("No puedes reasignar la cita a ti mismo.");
    }

    // Validar disponibilidad del agente destino (Sprint 5)
    agentAvailabilityService.checkAgentAvailability(
        dto.getDestinationAgentId(), event.getStartTime(), event.getEndTime());

    ReassignmentRequest request =
        new ReassignmentRequest(
            visitId, requestingAgentId, dto.getDestinationAgentId(), dto.getReason());
    request = reassignmentRequestRepository.save(request);

    reassignmentNotificationService.notifyReassignmentRequest(request, event);

    return ReassignmentRequestResponseDTO.from(request);
  }

  public ReassignmentRequestResponseDTO replyRequest(
      String requestId, String destinationAgentId, RequestResponseDTO dto) {
    ReassignmentRequest request =
        reassignmentRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + requestId));

    if (!request.getDestinationAgentId().equals(destinationAgentId)) {
      throw new RuntimeException("No tienes permiso para responder esta solicitud.");
    }

    if (request.getStatus() != ReassignmentRequest.RequestStatus.PENDING) {
      throw new RuntimeException("Esta solicitud ya fue respondida.");
    }

    if (dto.getDecision() == ReassignmentRequest.RequestStatus.PENDING) {
      throw new RuntimeException("La decisión debe ser ACEPTADA o RECHAZADA.");
    }

    request.setStatus(dto.getDecision());
    request.setRepliedAt(LocalDateTime.now());
    request.setCommentReply(dto.getComment());

    if (dto.getDecision() == ReassignmentRequest.RequestStatus.ACCEPTED) {
      // Validar disponibilidad al aceptar la reasignación (Sprint 5)
      CalendarEvent event =
          calendarEventRepository
              .findById(request.getVisitId())
              .orElseThrow(
                  () -> new RuntimeException("Cita no encontrada al verificar disponibilidad."));
      agentAvailabilityService.checkAgentAvailability(
          request.getDestinationAgentId(), event.getStartTime(), event.getEndTime());
      applyReassignment(request);
    }

    request = reassignmentRequestRepository.save(request);
    reassignmentNotificationService.notifyReassignmentDecision(request);

    return ReassignmentRequestResponseDTO.from(request);
  }

  private void applyReassignment(ReassignmentRequest request) {
    CalendarEvent event =
        calendarEventRepository
            .findById(request.getVisitId())
            .orElseThrow(() -> new RuntimeException("Cita no encontrada al aplicar reasignación."));

    String destinationAgentName = getAgentFullName(request.getDestinationAgentId());

    event.setAgentId(request.getDestinationAgentId());
    event.setAgentName(destinationAgentName);

    calendarEventRepository.save(event);
    log.info(
        "Reasignación aplicada: evento '{}' (propiedad '{}') ahora asignado a agente '{}' ('{}')",
        event.getId(),
        event.getPropertyName(),
        request.getDestinationAgentId(),
        destinationAgentName);

    visitRepository
        .findById(request.getVisitId())
        .ifPresent(
            visit -> {
              String previousAgent = visit.getAgentId();
              ReassignmentHistory reassignmentHistory =
                  new ReassignmentHistory(
                      request.getId(),
                      previousAgent,
                      request.getDestinationAgentId(),
                      request.getReason());

              if (visit.getReassignmentHistory() == null) {
                visit.setReassignmentHistory(new java.util.ArrayList<>());
              }
              visit.getReassignmentHistory().add(reassignmentHistory);
              visit.setAgentId(request.getDestinationAgentId());
              visitRepository.save(visit);
            });
  }

  private String getAgentFullName(String agentId) {
    try {
      Map<String, Object> response = userClient.getUserById(agentId);
      if (response != null) {
        String firstName = (String) response.get("firstName");
        String lastName = (String) response.get("lastName");
        if (firstName != null || lastName != null) {
          return (firstName != null ? firstName : "") + (lastName != null ? " " + lastName : "");
        }
      }
    } catch (Exception e) {
      log.warn(
          "Error getting name from user-service: {}. Trying person-service...", e.getMessage());
    }

    try {
      PersonResponse response = personClient.getPersonByAuthUserId(agentId);
      if (response != null && response.fullName() != null) {
        return response.fullName();
      } else if (response != null) {
        return response.firstName() + " " + response.lastName();
      }
    } catch (Exception e) {
      log.warn("Error getting name from person-service: {}", e.getMessage());
    }

    return agentId;
  }

  public List<ReassignmentRequestResponseDTO> getReceivedRequests(String destinationAgentId) {
    return reassignmentRequestRepository
        .findByDestinationAgentIdAndStatus(
            destinationAgentId, ReassignmentRequest.RequestStatus.PENDING)
        .stream()
        .map(ReassignmentRequestResponseDTO::from)
        .collect(Collectors.toList());
  }

  public long countPendingRequests(String destinationAgentId) {
    return reassignmentRequestRepository.countByDestinationAgentIdAndStatus(
        destinationAgentId, ReassignmentRequest.RequestStatus.PENDING);
  }

  public List<ReassignmentRequestResponseDTO> getSentRequests(String requestingAgentId) {
    return reassignmentRequestRepository.findByRequestingAgentId(requestingAgentId).stream()
        .map(ReassignmentRequestResponseDTO::from)
        .collect(Collectors.toList());
  }

  public void cancelRequest(String requestId, String requestingAgentId) {
    ReassignmentRequest request =
        reassignmentRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

    if (!request.getRequestingAgentId().equals(requestingAgentId)) {
      throw new RuntimeException("No tienes permiso para cancelar esta solicitud");
    }

    if (request.getStatus() != ReassignmentRequest.RequestStatus.PENDING) {
      throw new RuntimeException("Solo se pueden cancelar solicitudes pendientes");
    }

    reassignmentRequestRepository.deleteById(requestId);
  }
}

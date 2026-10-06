package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.ClientVisitRequestDTO;
import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.VisitRequestResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.service.VisitRequestService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para solicitudes de visita de clientes buscadores.
 *
 * <p>HU3: POST /visit-requests → Cliente solicita cita (PA1 + PA2) GET
 * /visit-requests/agent/{agentId} → Agente ve sus solicitudes pendientes GET
 * /visit-requests/client/{clientId} → Cliente ve sus propias solicitudes PATCH
 * /visit-requests/{id}/accept → Agente acepta → crea evento en calendario PATCH
 * /visit-requests/{id}/reject → Agente rechaza la solicitud
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/visit-requests")
public class VisitRequestController {

  private final VisitRequestService visitRequestService;
  private final ResponseFactory responseFactory;

  /**
   * POST /visit-requests
   *
   * <p>El cliente buscador solicita una cita para visitar un inmueble. PA1: El inmueble debe estar
   * "Disponible" (validado en property-service). PA2: Se genera notificación al agente responsable
   * del inmueble.
   *
   * <p>Body: ClientVisitRequestDTO Response 201: VisitRequestResponse
   */
  @PostMapping
  public ResponseEntity<ApiResponse<VisitRequestResponse>> createVisitRequest(
      @Valid @RequestBody ClientVisitRequestDTO dto) {

    log.debug(
        "POST /visit-requests: cliente={}, propiedad={}", dto.getClientId(), dto.getPropertyId());

    VisitRequestResponse response = visitRequestService.createVisitRequest(dto);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            responseFactory.created(
                "Solicitud de visita enviada correctamente. "
                    + "El agente responsable ha sido notificado.",
                response));
  }

  /**
   * GET /visit-requests/agent/{agentId}
   *
   * <p>El agente consulta sus solicitudes pendientes. Útil para el panel del agente donde ve las
   * solicitudes entrantes.
   */
  @GetMapping("/agent/{agentId}")
  public ResponseEntity<ApiResponse<List<VisitRequestResponse>>> getPendingForAgent(
      @PathVariable String agentId) {

    List<VisitRequestResponse> pending = visitRequestService.getPendingRequestsForAgent(agentId);

    return ResponseEntity.ok(responseFactory.success("Solicitudes pendientes obtenidas", pending));
  }

  /**
   * GET /visit-requests/client/{clientId}
   *
   * <p>El cliente consulta el estado de sus solicitudes enviadas.
   */
  @GetMapping("/client/{clientId}")
  public ResponseEntity<ApiResponse<List<VisitRequestResponse>>> getClientRequests(
      @PathVariable String clientId) {

    List<VisitRequestResponse> requests = visitRequestService.getClientRequests(clientId);

    return ResponseEntity.ok(
        responseFactory.success("Solicitudes del cliente obtenidas", requests));
  }

  @GetMapping("/count/property/{propertyId}")
  public ResponseEntity<ApiResponse<Integer>> getVisitCountForProperty(
      @PathVariable String propertyId) {

    // Security: Verify the authenticated user owns this property
    // We'll need to call property-service to check ownership

    int count = visitRequestService.getVisitCountForProperty(propertyId);
    return ResponseEntity.ok(responseFactory.success("Visit count retrieved", count));
  }

  /**
   * PATCH /visit-requests/{id}/accept
   *
   * <p>El agente acepta la solicitud. Crea automáticamente el evento en el calendario compartido.
   */
  @PatchMapping("/{id}/accept")
  public ResponseEntity<ApiResponse<VisitRequestResponse>> acceptRequest(
      @PathVariable String id,
      @RequestHeader("X-Agent-Id") String agentId,
      @RequestBody(required = false)
          com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.AcceptVisitRequestDTO
              customTime) {

    VisitRequestResponse response = visitRequestService.acceptVisitRequest(id, agentId, customTime);

    return ResponseEntity.ok(
        responseFactory.success(
            "Solicitud aceptada. La visita fue añadida al calendario.", response));
  }

  /**
   * PATCH /visit-requests/{id}/reject
   *
   * <p>El agente rechaza la solicitud.
   */
  @PatchMapping("/{id}/reject")
  public ResponseEntity<ApiResponse<VisitRequestResponse>> rejectRequest(
      @PathVariable String id, @RequestHeader("X-Agent-Id") String agentId) {

    VisitRequestResponse response = visitRequestService.rejectVisitRequest(id, agentId);

    return ResponseEntity.ok(responseFactory.success("Solicitud rechazada.", response));
  }
}

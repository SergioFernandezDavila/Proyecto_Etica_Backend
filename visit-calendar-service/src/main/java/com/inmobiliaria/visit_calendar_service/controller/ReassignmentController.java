package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.ReassignmentRequestRequestDTO;
import com.inmobiliaria.visit_calendar_service.dto.ReassignmentRequestResponseDTO;
import com.inmobiliaria.visit_calendar_service.dto.RequestResponseDTO;
import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.service.ReassignmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestión de reasignaciones de citas.
 *
 * <p>Endpoints implementados: POST /reassignments/visits/{id}/reassignment → Solicitar reasignación
 * PUT /reassignments/{id}/reply → Aceptar / Rechazar solicitud GET /reassignments/received →
 * Solicitudes pendientes del agente GET /reassignments/pending/count → Cantidad (para badge de
 * menú)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reassignments")
public class ReassignmentController {

  private final ReassignmentService reassignmentService;
  private final ResponseFactory responseFactory;

  // ─────────────────────────────────────────────────────────────────────────
  // POST /reassignments/visits/{id}/reassignment
  // Solicitar reasignación de una cita propia
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Crea una solicitud de reasignación para la cita indicada.
   *
   * @param visitId ID de la cita (path variable)
   * @param requestingAgentId ID del agente autenticado (inyectado por gateway)
   * @param dto destinationAgentId + motivo
   */
  @PostMapping("/visits/{id}/reassignment")
  public ResponseEntity<ApiResponse<ReassignmentRequestResponseDTO>> requestReassignment(
      @PathVariable("id") String visitId,
      @RequestHeader("X-Auth-User-Id") String requestingAgentId,
      @Valid @RequestBody ReassignmentRequestRequestDTO dto) {
    try {
      ReassignmentRequestResponseDTO response =
          reassignmentService.requestReassignment(visitId, requestingAgentId, dto);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(responseFactory.created("Solicitud de reasignación creada", response));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PUT /reassignments/{id}/reply
  // Aceptar o rechazar una solicitud de reasignación recibida
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * El agente destino acepta o rechaza una solicitud de reasignación.
   *
   * @param requestId ID de la solicitud (path variable)
   * @param destinationAgentId ID del agente autenticado (inyectado por gateway)
   * @param dto decision (ACEPTADA|RECHAZADA) + comentario opcional
   */
  @PutMapping("/{id}/reply")
  public ResponseEntity<ApiResponse<ReassignmentRequestResponseDTO>> replyRequest(
      @PathVariable("id") String requestId,
      @RequestHeader("X-Auth-User-Id") String destinationAgentId,
      @Valid @RequestBody RequestResponseDTO dto) {
    try {
      ReassignmentRequestResponseDTO response =
          reassignmentService.replyRequest(requestId, destinationAgentId, dto);
      return ResponseEntity.ok(responseFactory.success("Respuesta procesada", response));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // GET /reassignments/received
  // Bandeja de solicitudes pendientes del agente autenticado
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Retorna todas las solicitudes de reasignación PENDIENTES dirigidas al agente autenticado.
   *
   * @param destinationAgentId ID del agente autenticado (inyectado por gateway)
   */
  @GetMapping("/received")
  public ResponseEntity<ApiResponse<List<ReassignmentRequestResponseDTO>>> getReceivedRequests(
      @RequestHeader("X-Auth-User-Id") String destinationAgentId) {
    List<ReassignmentRequestResponseDTO> requests =
        reassignmentService.getReceivedRequests(destinationAgentId);
    return ResponseEntity.ok(responseFactory.success("Solicitudes recibidas obtenidas", requests));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // GET /reassignments/pending/count
  // Contador para badge de menú
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Retorna el número de solicitudes pendientes para mostrar en el badge de notificación del menú
   * lateral.
   *
   * @param destinationAgentId ID del agente autenticado
   */
  @GetMapping("/pending/count")
  public ResponseEntity<ApiResponse<Map<String, Long>>> countPendingRequests(
      @RequestHeader("X-Auth-User-Id") String destinationAgentId) {
    long count = reassignmentService.countPendingRequests(destinationAgentId);
    return ResponseEntity.ok(responseFactory.success("Conteo obtenido", Map.of("pending", count)));
  }

  // GET /reassignments/sent → Solicitudes enviadas por el agente autenticado
  @GetMapping("/sent")
  public ResponseEntity<ApiResponse<List<ReassignmentRequestResponseDTO>>> getSentRequests(
      @RequestHeader("X-Auth-User-Id") String requestingAgentId) {
    List<ReassignmentRequestResponseDTO> requests =
        reassignmentService.getSentRequests(requestingAgentId);
    return ResponseEntity.ok(responseFactory.success("Solicitudes enviadas obtenidas", requests));
  }

  // DELETE /reassignments/{id} → Cancelar solicitud pendiente
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> cancelRequest(
      @PathVariable("id") String requestId,
      @RequestHeader("X-Auth-User-Id") String requestingAgentId) {
    try {
      reassignmentService.cancelRequest(requestId, requestingAgentId);
      return ResponseEntity.ok(responseFactory.success("Solicitud cancelada exitosamente", null));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }
}

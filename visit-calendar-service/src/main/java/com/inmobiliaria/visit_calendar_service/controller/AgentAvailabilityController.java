package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.AvailabilityTemplateApplyRequest;
import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.model.AgentAvailability;
import com.inmobiliaria.visit_calendar_service.model.AvailabilityTemplate;
import com.inmobiliaria.visit_calendar_service.service.AgentAvailabilityService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class AgentAvailabilityController {

  private final AgentAvailabilityService availabilityService;
  private final ResponseFactory responseFactory;

  // 1. Availability slots endpoints
  @GetMapping("/agents/{id}/availability")
  public ResponseEntity<ApiResponse<List<AgentAvailability>>> getAgentAvailability(
      @PathVariable("id") String agentId) {
    log.debug("GET /agents/{}/availability", agentId);
    List<AgentAvailability> list = availabilityService.getAgentAvailability(agentId);
    return ResponseEntity.ok(
        responseFactory.success("Disponibilidad del agente obtenida correctamente", list));
  }

  @PostMapping("/agents/{id}/availability")
  public ResponseEntity<ApiResponse<AgentAvailability>> saveAvailability(
      @PathVariable("id") String agentId, @Valid @RequestBody AgentAvailability availability) {
    log.debug("POST /agents/{}/availability", agentId);
    try {
      AgentAvailability saved = availabilityService.saveAvailability(agentId, availability);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(responseFactory.created("Horario de disponibilidad registrado", saved));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  @PutMapping("/agents/{id}/availability/{slotId}")
  public ResponseEntity<ApiResponse<AgentAvailability>> updateAvailability(
      @PathVariable("id") String agentId,
      @PathVariable("slotId") String slotId,
      @Valid @RequestBody AgentAvailability availability) {
    log.debug("PUT /agents/{}/availability/{}", agentId, slotId);
    try {
      AgentAvailability updated =
          availabilityService.updateAvailability(agentId, slotId, availability);
      return ResponseEntity.ok(
          responseFactory.success("Horario de disponibilidad modificado", updated));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  @DeleteMapping("/agents/{id}/availability/{slotId}")
  public ResponseEntity<ApiResponse<Void>> deleteAvailability(
      @PathVariable("id") String agentId, @PathVariable("slotId") String slotId) {
    log.debug("DELETE /agents/{}/availability/{}", agentId, slotId);
    try {
      availabilityService.deleteAvailability(agentId, slotId);
      return ResponseEntity.ok(
          responseFactory.success("Horario de disponibilidad eliminado", null));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  // 2. Availability Templates endpoints
  @PostMapping("/availability-templates")
  public ResponseEntity<ApiResponse<AvailabilityTemplate>> createTemplate(
      @Valid @RequestBody AvailabilityTemplate template) {
    log.debug("POST /availability-templates");
    AvailabilityTemplate created = availabilityService.createTemplate(template);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseFactory.created("Plantilla de disponibilidad creada", created));
  }

  @PutMapping("/availability-templates/{id}")
  public ResponseEntity<ApiResponse<AvailabilityTemplate>> updateTemplate(
      @PathVariable("id") String templateId, @Valid @RequestBody AvailabilityTemplate template) {
    log.debug("PUT /availability-templates/{}", templateId);
    try {
      AvailabilityTemplate updated = availabilityService.updateTemplate(templateId, template);
      return ResponseEntity.ok(
          responseFactory.success("Plantilla de disponibilidad modificada", updated));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  @GetMapping("/availability-templates")
  public ResponseEntity<ApiResponse<List<AvailabilityTemplate>>> getTemplates() {
    log.debug("GET /availability-templates");
    List<AvailabilityTemplate> list = availabilityService.getTemplates();
    return ResponseEntity.ok(
        responseFactory.success("Plantillas de disponibilidad obtenidas", list));
  }

  @DeleteMapping("/availability-templates/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable("id") String templateId) {
    log.debug("DELETE /availability-templates/{}", templateId);
    try {
      availabilityService.deleteTemplate(templateId);
      return ResponseEntity.ok(
          responseFactory.success("Plantilla de disponibilidad eliminada", null));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }

  @PostMapping("/availability-templates/{id}/apply")
  public ResponseEntity<ApiResponse<Void>> applyTemplate(
      @PathVariable("id") String templateId,
      @Valid @RequestBody AvailabilityTemplateApplyRequest request) {
    log.debug("POST /availability-templates/{}/apply", templateId);
    try {
      availabilityService.applyTemplate(templateId, request.getAgentIds(), request.isOverwrite());
      return ResponseEntity.ok(
          responseFactory.success("Plantilla aplicada exitosamente a los agentes", null));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(responseFactory.error(e.getMessage()));
    }
  }
}

package com.inmobiliaria.visit_calendar_service.dto;

import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.model.VisitRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Clases DTO para el visit-calendar-service. Separadas en clases estáticas internas para mantener
 * el código organizado.
 */
public class VisitCalendarDTOs {

  // =========================================================
  //  HU1 + HU2: CalendarEvent DTOs
  // =========================================================

  /** Request para crear un evento de visita (HU2 - programar visita). */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreateVisitRequest {

    @NotBlank(message = "El ID del inmueble es obligatorio")
    private String propertyId;

    @NotBlank(message = "El nombre del inmueble es obligatorio")
    private String propertyName;

    private String propertyAddress;

    @NotBlank(message = "El ID del agente es obligatorio")
    private String agentId;

    @NotBlank(message = "El nombre del agente es obligatorio")
    private String agentName;

    @NotNull(message = "La fecha/hora de inicio es obligatoria")
    private Instant startTime;

    @NotNull(message = "La fecha/hora de fin es obligatoria")
    private Instant endTime;

    private String notes;
  }

  /** Response del GET /calendar con lista de eventos y metadatos. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CalendarResponse {
    private List<Visit> events;
    private Instant from;
    private Instant to;
    private int totalEvents;
    private int myEvents;
  }

  /** Response cuando hay un conflicto de horario (HU1 PA2 / HU2 PA2). */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ConflictResponse {
    private boolean hasConflict;
    private String message;
    private List<Visit> conflictingEvents;

    /** Sugerencia de horario alternativo */
    private Instant suggestedStartTime;

    private Instant suggestedEndTime;
  }

  // =========================================================
  //  HU3: VisitRequest DTOs (cliente solicita visita)
  // =========================================================

  /** Request del cliente para solicitar una cita (HU3). */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ClientVisitRequestDTO {

    @NotBlank(message = "El ID de la propiedad es obligatorio")
    private String propertyId;

    @NotBlank(message = "El nombre de la propiedad es obligatorio")
    private String propertyName;

    @NotBlank(message = "El ID del agente responsable es obligatorio")
    private String agentId;

    @NotBlank(message = "El nombre del agente es obligatorio")
    private String agentName;

    @NotBlank(message = "El ID del cliente es obligatorio")
    private String clientId;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String clientName;

    @NotBlank(message = "El email del cliente es obligatorio")
    private String clientEmail;

    private String clientPhone;

    @NotNull(message = "El horario preferido es obligatorio")
    private Instant preferredDateTime;

    private Instant alternativeDateTime;

    private String message;
  }

  /** Response de la solicitud de visita del cliente. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VisitRequestResponse {
    private String id;
    private String propertyId;
    private String propertyName;
    private String agentId;
    private String agentName;
    private String clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private Instant preferredDateTime;
    private Instant alternativeDateTime;
    private String message;
    private VisitRequest.RequestStatus status;
    private String calendarEventId;
    private Instant createdAt;
    private boolean notificationSent;
    private Instant acceptedDateTime;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AcceptVisitRequestDTO {
    private Instant customStartTime;
    private Instant customEndTime;
    private String vehicleId;
  }

  // =========================================================
  //  Respuesta genérica de error/éxito
  // =========================================================

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(String message, T data) {
      return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    public static <T> ApiResponse<T> error(String message) {
      return ApiResponse.<T>builder().success(false).message(message).build();
    }
  }
}

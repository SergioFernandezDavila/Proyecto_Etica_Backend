package com.inmobiliaria.visit_calendar_service.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

/**
 * Request body for POST /visits/{id}/reschedule
 *
 * <p>TD: DTOs for the reschedule endpoint.
 *
 * <p>The agent provides the new date and time for the rescheduled visit. The visit being
 * rescheduled must be in CANCELLED status (enforced in RescheduleService).
 */
@Data
public class RescheduleRequest {

  /**
   * New date and time for the rescheduled visit. Must be a future datetime — cannot schedule a
   * visit in the past.
   */
  @NotNull(message = "The new date and time are required.")
  @Future(message = "The new date and time must be in the future.")
  private Instant newStartTime;

  private Instant newEndTime;

  /**
   * Optional notes for the new visit. If not provided, the original visit's notes are copied over.
   */
  private String notes;
}

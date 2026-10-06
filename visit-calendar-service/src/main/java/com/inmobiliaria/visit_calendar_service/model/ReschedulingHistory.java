package com.inmobiliaria.visit_calendar_service.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded document that records each rescheduling event in the visit's history.
 *
 * <p>TD: Persist the rescheduling history by linking visits.
 *
 * <p>Stored as a list inside the Visit document. Each entry captures: when the reschedule happened,
 * who requested it, and what the previous scheduled date was before the change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReschedulingHistory {

  /** ID of the new visit created by this rescheduling action */
  private String newVisitId;

  /** The date/time that was replaced (the old scheduled date of the cancelled visit) */
  private Instant previousStartTime;

  private Instant previousEndTime;

  /** The new date/time assigned to the newly created visit */
  private Instant newStartTime;

  private Instant newEndTime;

  /** ID of the agent who performed the rescheduling */
  private String rescheduledByAgentId;

  /** Timestamp when this rescheduling action was performed */
  private Instant rescheduledAt;
}

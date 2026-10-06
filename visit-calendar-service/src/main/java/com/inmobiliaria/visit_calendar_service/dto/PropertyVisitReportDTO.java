package com.inmobiliaria.visit_calendar_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single row in the monthly property visit ranking report.
 *
 * <p>Each instance maps to one property and contains its aggregated visit count for the requested
 * month, along with the responsible agent information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyVisitReportDTO {

  /** Position in the ranking (1-based, highest visit count first) */
  private int rank;

  /** Unique identifier of the property */
  private String propertyId;

  /** Street address of the property */
  private String propertyAddress;

  /** Display name of the property */
  private String propertyName;

  /** Total number of visits recorded for this property in the selected month */
  private long visitCount;

  /** ID of the agent currently assigned to this property */
  private String agentId;

  /** Full name of the agent currently assigned to this property */
  private String agentName;
}

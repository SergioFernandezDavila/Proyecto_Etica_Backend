package com.inmobiliaria.visit_calendar_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for the monthly visit report response. Contains the period metadata and the ranked list
 * of properties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitReportResponse {

  /** Month of the report (1–12) */
  private int month;

  /** Year of the report */
  private int year;

  /** Total number of distinct properties with at least one visit in the period */
  private int totalProperties;

  /** Properties ranked by visit count, descending (AC8) */
  private List<PropertyVisitReportDTO> rankings;
}

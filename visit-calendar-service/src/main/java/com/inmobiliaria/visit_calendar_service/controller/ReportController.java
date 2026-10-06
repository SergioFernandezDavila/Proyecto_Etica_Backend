package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.VisitReportResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ApiResponse;
import com.inmobiliaria.visit_calendar_service.dto.response.ResponseFactory;
import com.inmobiliaria.visit_calendar_service.service.VisitReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for visit reporting endpoints.
 *
 * <p>Exposes read-only report endpoints that aggregate visit data. Separated from {@link
 * CalendarController} to keep controllers focused (SRP): CalendarController manages CRUD on
 * calendar events, while this controller serves analytical views.
 *
 * <p>Endpoint: GET /reports/visits-by-property?month=X&year=Y
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

  private final VisitReportService visitReportService;
  private final ResponseFactory responseFactory;

  /**
   * Returns a ranking of properties by total visits in the given month.
   *
   * <p>Acceptance criteria covered:
   *
   * <ul>
   *   <li>AC6: Returns empty rankings with descriptive message when no visits exist
   *   <li>AC7: Filtering by month/year allows period switching
   *   <li>AC8: Rankings are pre-sorted by visitCount descending
   * </ul>
   *
   * @param month Month number (1–12)
   * @param year Four-digit year (e.g. 2025)
   * @return 200 with VisitReportResponse (possibly empty), 400 for invalid params
   */
  @GetMapping("/visits-by-property")
  public ResponseEntity<ApiResponse<VisitReportResponse>> getVisitsByProperty(
      @RequestParam int month, @RequestParam int year) {

    log.debug("GET /reports/visits-by-property: month={}, year={}", month, year);

    // Input validation
    if (month < 1 || month > 12) {
      return ResponseEntity.badRequest()
          .body(
              responseFactory.validationError(
                  "Parámetros inválidos: el mes debe estar entre 1 y 12",
                  "month",
                  "VALIDATION_ERROR",
                  "El mes debe estar entre 1 y 12"));
    }

    if (year < 2000 || year > 2100) {
      return ResponseEntity.badRequest()
          .body(
              responseFactory.validationError(
                  "Parámetros inválidos: el año debe estar entre 2000 y 2100",
                  "year",
                  "VALIDATION_ERROR",
                  "El año debe estar entre 2000 y 2100"));
    }

    VisitReportResponse report = visitReportService.generateMonthlyReport(month, year);

    // AC6: descriptive message when no visits found
    String message =
        report.getRankings().isEmpty()
            ? "No se encontraron visitas registradas para el período seleccionado"
            : "Reporte de visitas por propiedad obtenido correctamente";

    return ResponseEntity.ok(responseFactory.success(message, report));
  }
}

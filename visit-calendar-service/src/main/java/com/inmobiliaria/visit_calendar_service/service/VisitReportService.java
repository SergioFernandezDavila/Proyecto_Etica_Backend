package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.dto.PropertyVisitReportDTO;
import com.inmobiliaria.visit_calendar_service.dto.VisitReportResponse;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Service responsible for generating monthly visit ranking reports.
 *
 * <p>Aggregates visits from the {@code calendar_events} collection grouped by property, sorted by
 * visit count descending (AC8). Returns an empty list with a descriptive message when no visits
 * exist for the period (AC6).
 *
 * <p>This service is intentionally decoupled from {@link CalendarService} to respect the Single
 * Responsibility Principle. CalendarService handles calendar operations; this class handles
 * reporting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReportService {

  private final MongoTemplate mongoTemplate;

  /**
   * Generates the monthly visit ranking report for all properties.
   *
   * <p>Uses MongoDB aggregation to:
   *
   * <ol>
   *   <li>Filter events within the month/year range and with non-cancelled status
   *   <li>Group by propertyId, counting visits and preserving property/agent metadata
   *   <li>Sort by visit count descending
   * </ol>
   *
   * @param month Month number (1–12), already validated by the controller
   * @param year Four-digit year, already validated by the controller
   * @return A fully populated {@link VisitReportResponse}
   */
  public VisitReportResponse generateMonthlyReport(int month, int year) {
    YearMonth yearMonth = YearMonth.of(year, month);
    Instant startOfMonth = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant startOfNextMonth =
        yearMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

    log.debug(
        "Generating monthly report: month={}, year={}, range=[{}, {})",
        month,
        year,
        startOfMonth,
        startOfNextMonth);

    // MongoDB aggregation pipeline: match → group → sort
    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(
                Criteria.where("startTime")
                    .gte(startOfMonth)
                    .lt(startOfNextMonth)
                    .and("status")
                    .nin("CANCELLED")),
            Aggregation.group("propertyId")
                .count()
                .as("visitCount")
                .first("propertyName")
                .as("propertyName")
                .first("propertyAddress")
                .as("propertyAddress")
                .first("agentId")
                .as("agentId")
                .first("agentName")
                .as("agentName"),
            Aggregation.sort(
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "visitCount")));

    AggregationResults<PropertyVisitAggregationResult> results =
        mongoTemplate.aggregate(
            aggregation, "calendar_events", PropertyVisitAggregationResult.class);

    List<PropertyVisitAggregationResult> rawResults = results.getMappedResults();

    // Map aggregation results to DTOs with rank
    AtomicInteger rankCounter = new AtomicInteger(1);
    List<PropertyVisitReportDTO> rankings =
        rawResults.stream()
            .map(
                row ->
                    PropertyVisitReportDTO.builder()
                        .rank(rankCounter.getAndIncrement())
                        .propertyId(row.getId())
                        .propertyAddress(row.getPropertyAddress())
                        .propertyName(row.getPropertyName())
                        .visitCount(row.getVisitCount())
                        .agentId(row.getAgentId())
                        .agentName(row.getAgentName())
                        .build())
            .collect(Collectors.toList());

    log.info(
        "Monthly report generated: month={}, year={}, propertiesFound={}",
        month,
        year,
        rankings.size());

    return VisitReportResponse.builder()
        .month(month)
        .year(year)
        .totalProperties(rankings.size())
        .rankings(rankings)
        .build();
  }

  /**
   * Internal POJO to capture the MongoDB aggregation result. The {@code _id} field maps to
   * propertyId in the $group stage.
   */
  @lombok.Data
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  static class PropertyVisitAggregationResult {

    @org.springframework.data.annotation.Id private String id;

    private String propertyName;
    private String propertyAddress;
    private long visitCount;
    private String agentId;
    private String agentName;
  }
}

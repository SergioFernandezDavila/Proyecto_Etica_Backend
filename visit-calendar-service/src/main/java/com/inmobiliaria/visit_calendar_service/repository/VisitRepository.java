package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.model.Visit.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for Visit documents.
 *
 * <p>TD: Add availability validation queries needed by RescheduleService.
 */
@Repository
public interface VisitRepository extends MongoRepository<Visit, String> {

  /** Todas las citas de un agente (para su agenda) */
  List<Visit> findByAgentId(String agentId);

  /** Citas de un agente en un rango de fechas */
  List<Visit> findByAgentIdAndStartTimeBetween(String agentId, Instant start, Instant end);

  /** Citas de un cliente */
  List<Visit> findByClientId(String clientId);

  /** Citas de un inmueble */
  List<Visit> findByPropertyId(String propertyId);

  // ── NEW methods needed for rescheduling availability check ────────────

  /**
   * Checks if the agent already has a SCHEDULED visit within a 1-hour window around the proposed
   * new datetime.
   *
   * <p>Used by RescheduleService to prevent double-booking the agent.
   *
   * @param agentId Agent to check
   * @param windowStart Start of the conflict window (newDateTime minus buffer)
   * @param windowEnd End of the conflict window (newDateTime plus buffer)
   * @param status Only check against SCHEDULED visits
   */
  boolean existsByAgentIdAndStartTimeBetweenAndStatus(
      String agentId, Instant windowStart, Instant windowEnd, EventStatus status);

  /**
   * Checks if the property already has a SCHEDULED visit within a 1-hour window.
   *
   * <p>Used to prevent scheduling two visits to the same property at the same time.
   */
  boolean existsByPropertyIdAndStartTimeBetweenAndStatus(
      String propertyId, Instant windowStart, Instant windowEnd, EventStatus status);

  /**
   * Returns all visits linked to a specific origin visit (i.e., all rescheduled visits that
   * originated from a given cancelled visit).
   *
   * <p>Used to display the rescheduling chain in the UI.
   */
  List<Visit> findByOriginVisitId(String originVisitId);

  /** Returns all visits for an agent with a specific status. */
  List<Visit> findByAgentIdAndStatus(String agentId, EventStatus status);

  /**
   * Encuentra visitas que tienen un vehículo asignado y se solapan con el rango de tiempo dado.
   * Usado para detección de conflictos de flota agnóstica.
   */
  @org.springframework.data.mongodb.repository.Query(
      "{ 'vehicleId': ?0, 'status': 'SCHEDULED', "
          + "$or: [ "
          + "  { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } "
          + "] }")
  List<Visit> findConflictingVehicles(String vehicleId, Instant start, Instant end);

  // Encuentra una visita por ID solo si tiene un estado específico (valida que solo se puedan
  // registrar en visitas COMPLETED)
  Optional<Visit> findByIdAndStatus(String id, EventStatus status);

  List<Visit> findByStartTimeBetweenAndStatus(Instant start, Instant end, EventStatus status);

  List<Visit> findByAgentIdAndStartTimeBetweenAndStatus(
      String agentId, Instant start, Instant end, EventStatus status);

  List<Visit> findByStartTimeBetween(Instant start, Instant end);
}

package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio MongoDB para CalendarEvent. Contiene queries para el calendario compartido, filtros y
 * detección de conflictos.
 */
@Repository
public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {

  // --- Consultas para HU1: Visualizar calendario compartido ---

  /** Obtiene todos los eventos en un rango de fechas (vista semanal/mensual). */
  @Query("{ 'startTime': { $gte: ?0 }, 'endTime': { $lte: ?1 } }")
  List<CalendarEvent> findByDateRange(Instant from, Instant to);

  /** Obtiene eventos de un agente específico en un rango de fechas. */
  @Query("{ 'agentId': ?0, 'startTime': { $gte: ?1 }, 'endTime': { $lte: ?2 } }")
  List<CalendarEvent> findByAgentIdAndDateRange(String agentId, Instant from, Instant to);

  /** Obtiene todos los eventos de una propiedad específica. */
  @Query("{ 'propertyId': ?0 }")
  List<CalendarEvent> findByPropertyId(String propertyId);

  /** Obtiene eventos de una propiedad en un rango de fechas. */
  @Query("{ 'propertyId': ?0, 'startTime': { $gte: ?1 }, 'endTime': { $lte: ?2 } }")
  List<CalendarEvent> findByPropertyIdAndDateRange(String propertyId, Instant from, Instant to);

  // --- Consultas para HU2: Detección de conflictos de horario ---

  /**
   * Detecta si existe un conflicto de horario para una propiedad específica. Un conflicto ocurre
   * cuando el nuevo evento se solapa con uno existente: - El nuevo evento empieza ANTES de que
   * termine uno existente, Y - El nuevo evento termina DESPUÉS de que empiece uno existente. Se
   * excluye el evento con excludeId para poder hacer actualizaciones sin falso positivo.
   */
  @Query(
      "{ "
          + "  'propertyId': ?0, "
          + "  'status': { $nin: ['CANCELLED'] }, "
          + "  '_id': { $ne: ?3 }, "
          + "  '$or': [ "
          + "    { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } "
          + "  ] "
          + "}")
  List<CalendarEvent> findConflictingEvents(
      String propertyId, Instant newStart, Instant newEnd, String excludeId);

  /** Versión sin excludeId para nuevos eventos (sin ID todavía). */
  @Query(
      "{ "
          + "  'propertyId': ?0, "
          + "  'status': { $nin: ['CANCELLED'] }, "
          + "  '$or': [ "
          + "    { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } "
          + "  ] "
          + "}")
  List<CalendarEvent> findConflictingEventsForNew(
      String propertyId, Instant newStart, Instant newEnd);

  /** Detecta si existe un conflicto de horario para un agente específico. */
  @Query(
      "{ "
          + "  'agentId': ?0, "
          + "  'status': { $nin: ['CANCELLED'] }, "
          + "  '$or': [ "
          + "    { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } "
          + "  ] "
          + "}")
  List<CalendarEvent> findConflictingEventsForAgent(
      String agentId, Instant newStart, Instant newEnd);

  // --- Consultas generales ---

  List<CalendarEvent> findByAgentId(String agentId);

  @Query("{ 'startTime': { $gte: ?0, $lt: ?1 }, 'agentId': ?2 }")
  List<CalendarEvent> findByDayAndAgent(Instant dayStart, Instant dayEnd, String agentId);

  /**
   * Detecta conflictos de flota considerando la ventana de ocupación (incluyendo tránsito). Un
   * conflicto ocurre si el vehículo ya está reservado (estatus CONFIRMED o SCHEDULED) y los
   * horarios se solapan.
   */
  @Query(
      "{ "
          + "  'vehicleId': ?0, "
          + "  'status': { $in: ['CONFIRMED', 'SCHEDULED'] }, "
          + "  '$or': [ "
          + "    { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } "
          + "  ] "
          + "}")
  List<CalendarEvent> findConflictingVehicles(
      String vehicleId, Instant occupancyStart, Instant occupancyEnd);

  // --- Consultas para HU de historial de visitas (realizada) ---

  /**
   * Obtiene todas las visitas completadas de una propiedad, ordenadas por fecha descendente (más
   * recientes primero).
   */
  @Query("{ 'propertyId': ?0, 'status': 'COMPLETED' }")
  List<CalendarEvent> findCompletedVisitsByProperty(String propertyId);

  /**
   * Obtiene todas las visitas completadas de una propiedad dentro de un rango de fechas, ordenadas
   * por fecha descendente.
   */
  @Query("{ 'propertyId': ?0, 'status': 'COMPLETED', 'startTime': { $gte: ?1, $lte: ?2 } }")
  List<CalendarEvent> findCompletedVisitsByPropertyAndDateRange(
      String propertyId, Instant dateSince, Instant dateUntil);

  /** Cuenta el total de visitas completadas de una propiedad. */
  long countByPropertyIdAndStatus(String propertyId, CalendarEvent.EventStatus status);

  /** Cuenta visitas completadas de una propiedad con resultado "INTERESADO". */
  @Query("{ 'propertyId': ?0, 'status': 'COMPLETED', 'resultado': 'INTERESADO' }")
  long countCompletedInterestedByProperty(String propertyId);

  /**
   * Cuenta visitas completadas de una propiedad con resultado "INTERESADO" en un rango de fechas.
   */
  @Query(
      "{ 'propertyId': ?0, 'status': 'COMPLETED', 'resultado': 'INTERESADO', 'startTime': { $gte: ?1, $lte: ?2 } }")
  long countCompletedInterestedByPropertyAndDateRange(
      String propertyId, Instant dateSince, Instant dateUntil);
}

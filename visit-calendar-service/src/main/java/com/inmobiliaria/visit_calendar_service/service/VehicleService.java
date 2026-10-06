package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.exception.ResourceNotFoundException;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.Vehicle;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import com.inmobiliaria.visit_calendar_service.repository.VehicleRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio agnóstico para la gestión de flota y asignación de vehículos. Implementa un patrón de
 * agregador para asegurar la consistencia de datos entre diferentes tipos de entidades
 * (CalendarEvent y Visit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

  private final VehicleRepository vehicleRepository;
  private final CalendarEventRepository calendarEventRepository;
  private final VisitRepository visitRepository;

  // ─── Gestión de Vehículos (CRUD) ─────────────────────────────────────────

  public List<Vehicle> getAllVehicles() {
    return vehicleRepository.findAll();
  }

  public Vehicle getVehicleById(String id) {
    return vehicleRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado: " + id));
  }

  public Vehicle createVehicle(Vehicle vehicle) {
    return vehicleRepository.save(vehicle);
  }

  public Vehicle updateVehicle(String id, Vehicle vehicle) {
    Vehicle existing =
        vehicleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado: " + id));
    existing.setLicensePlate(vehicle.getLicensePlate());
    existing.setBrand(vehicle.getBrand());
    existing.setModel(vehicle.getModel());
    existing.setPassengerCapacity(vehicle.getPassengerCapacity());
    existing.setStatus(vehicle.getStatus());
    return vehicleRepository.save(existing);
  }

  public void deleteVehicle(String id) {
    vehicleRepository.deleteById(id);
  }

  public List<Vehicle> getAvailableVehicles(Instant dateTime) {
    List<Vehicle> allVehicles = vehicleRepository.findAll();
    return allVehicles.stream()
        .filter(v -> v.getStatus() != Vehicle.VehicleStatus.MAINTENANCE)
        .filter(v -> isVehicleAvailableAt(v.getId(), dateTime))
        .toList();
  }

  private boolean isVehicleAvailableAt(String vehicleId, Instant dateTime) {
    // Check conflicts at this specific time.
    // We use dateTime as both start and end to find any occupancy that contains this point.
    List<CalendarEvent> eventConflicts =
        calendarEventRepository.findConflictingVehicles(
            vehicleId, dateTime, dateTime.plus(1, ChronoUnit.SECONDS));
    if (!eventConflicts.isEmpty()) return false;

    List<Visit> visitConflicts =
        visitRepository.findConflictingVehicles(
            vehicleId, dateTime, dateTime.plus(1, ChronoUnit.SECONDS));
    if (!visitConflicts.isEmpty()) return false;

    return true;
  }

  // ─── Lógica Agnóstica de Disponibilidad ──────────────────────────────────

  /**
   * Verifica la disponibilidad de un vehículo consultando TODAS las fuentes de ocupación. Esto
   * asegura que un vehículo no sea reservado doblemente si existe en CalendarEvent o en Visit.
   */
  public void checkVehicleAvailability(
      String vehicleId, Instant start, Instant end, String excludeId) {
    Vehicle vehicle =
        vehicleRepository
            .findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado"));

    if (vehicle.getStatus() == Vehicle.VehicleStatus.MAINTENANCE) {
      throw new RuntimeException("El vehículo está en mantenimiento y no puede ser asignado.");
    }

    // 1. Check conflicts in CalendarEvent
    List<CalendarEvent> eventConflicts =
        calendarEventRepository.findConflictingVehicles(vehicleId, start, end);
    boolean hasEventConflict = eventConflicts.stream().anyMatch(e -> !e.getId().equals(excludeId));

    if (hasEventConflict) {
      throw new RuntimeException(
          "Conflicto de horario: El vehículo ya está reservado o en tránsito en CalendarEvent.");
    }

    // 2. Check conflicts in Visit (Agnostic Aggregation)
    List<Visit> visitConflicts = visitRepository.findConflictingVehicles(vehicleId, start, end);
    boolean hasVisitConflict = visitConflicts.stream().anyMatch(v -> !v.getId().equals(excludeId));

    if (hasVisitConflict) {
      throw new RuntimeException(
          "Conflicto de horario: El vehículo ya está reservado o en tránsito en Visits (Reschedule).");
    }
  }

  // ─── Asignación de Vehículos ─────────────────────────────────────────────

  /** Asigna un vehículo a un CalendarEvent. */
  public Visit assignVehicleToVisit(
      String eventId, String vehicleId, Integer travelGo, Integer travelBack) {
    CalendarEvent event =
        calendarEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Evento de calendario no encontrado"));

    Instant occupancyStart =
        event.getStartTime().minus(travelGo != null ? travelGo : 0, ChronoUnit.MINUTES);
    Instant occupancyEnd =
        event.getEndTime().plus(travelBack != null ? travelBack : 0, ChronoUnit.MINUTES);

    checkVehicleAvailability(vehicleId, occupancyStart, occupancyEnd, eventId);

    event.setVehicleId(vehicleId);
    event.setTravelTimeGo(travelGo);
    event.setTravelTimeBack(travelBack);
    event.setStatus(CalendarEvent.EventStatus.CONFIRMED);

    CalendarEvent saved = calendarEventRepository.save(event);

    // Convert CalendarEvent to Visit
    Visit visit = new Visit();
    visit.setId(saved.getId());
    visit.setPropertyId(saved.getPropertyId());
    visit.setPropertyName(saved.getPropertyName());
    visit.setPropertyAddress(saved.getPropertyAddress());
    visit.setAgentId(saved.getAgentId());
    visit.setAgentName(saved.getAgentName());
    visit.setVehicleId(saved.getVehicleId());
    visit.setTravelTimeGo(saved.getTravelTimeGo());
    visit.setTravelTimeBack(saved.getTravelTimeBack());
    visit.setStartTime(saved.getStartTime());
    visit.setEndTime(saved.getEndTime());
    visit.setType(Visit.EventType.valueOf(saved.getType().name()));
    visit.setStatus(Visit.EventStatus.valueOf(saved.getStatus().name()));
    visit.setNotes(saved.getNotes());
    visit.setCreatedAt(saved.getCreatedAt());
    visit.setClientId(saved.getClientId());
    visit.setClientName(saved.getClientName());

    return visit;
  }

  /**
   * Asigna un vehículo a una Visit (Soporte para Reschedule). Mantiene la cohesión sin duplicar la
   * lógica de negocio en el servicio del otro dev.
   */
  public Visit assignVehicleToRescheduledVisit(
      String visitId, String vehicleId, Integer travelGo, Integer travelBack) {
    Visit visit =
        visitRepository
            .findById(visitId)
            .orElseThrow(() -> new ResourceNotFoundException("Visita no encontrada"));

    Instant occupancyStart =
        visit.getStartTime().minus(travelGo != null ? travelGo : 0, ChronoUnit.MINUTES);
    Instant occupancyEnd =
        visit.getEndTime().plus(travelBack != null ? travelBack : 0, ChronoUnit.MINUTES);

    checkVehicleAvailability(vehicleId, occupancyStart, occupancyEnd, visitId);

    visit.setVehicleId(vehicleId);
    visit.setTravelTimeGo(travelGo);
    visit.setTravelTimeBack(travelBack);
    // Nota: El otro dev usa VisitStatus, asumimos que la asignación confirma la logística.

    return visitRepository.save(visit);
  }

  public Visit getVisitWithAssignment(String visitId) {
    CalendarEvent event =
        calendarEventRepository
            .findById(visitId)
            .orElseThrow(() -> new ResourceNotFoundException("Visita no encontrada"));

    // Convert CalendarEvent to Visit
    Visit visit = new Visit();
    visit.setId(event.getId());
    visit.setPropertyId(event.getPropertyId());
    visit.setPropertyName(event.getPropertyName());
    visit.setPropertyAddress(event.getPropertyAddress());
    visit.setAgentId(event.getAgentId());
    visit.setAgentName(event.getAgentName());
    visit.setVehicleId(event.getVehicleId());
    visit.setTravelTimeGo(event.getTravelTimeGo());
    visit.setTravelTimeBack(event.getTravelTimeBack());
    visit.setStartTime(event.getStartTime());
    visit.setEndTime(event.getEndTime());
    visit.setType(Visit.EventType.valueOf(event.getType().name()));
    visit.setStatus(Visit.EventStatus.valueOf(event.getStatus().name()));
    visit.setNotes(event.getNotes());
    visit.setCreatedAt(event.getCreatedAt());
    visit.setClientId(event.getClientId());
    visit.setClientName(event.getClientName());

    return visit;
  }
}

package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.exception.ScheduleConflictException;
import com.inmobiliaria.visit_calendar_service.model.AgentAvailability;
import com.inmobiliaria.visit_calendar_service.model.AvailabilityTemplate;
import com.inmobiliaria.visit_calendar_service.repository.AgentAvailabilityRepository;
import com.inmobiliaria.visit_calendar_service.repository.AvailabilityTemplateRepository;
import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAvailabilityService {

  private final AgentAvailabilityRepository availabilityRepository;
  private final AvailabilityTemplateRepository templateRepository;

  /**
   * Seeds the default "Standard Office Hours" template if it does not exist. Mon-Fri: 08:30-12:30
   * and 14:30-18:30
   */
  @PostConstruct
  public void seedStandardTemplate() {
    log.info("Checking template: Horario de Oficina Estándar...");
    templateRepository
        .findByIsStandard(true)
        .ifPresent(
            t -> {
              log.info("Deleting old template to re-seed...");
              templateRepository.delete(t);
            });

    log.info("Seeding Horario de Oficina Estándar template...");
    List<AvailabilityTemplate.TemplateSlot> slots = new ArrayList<>();

    DayOfWeek[] workDays = {
      DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    for (DayOfWeek day : workDays) {
      slots.add(
          AvailabilityTemplate.TemplateSlot.builder()
              .type(AgentAvailability.SlotType.RECURRING)
              .dayOfWeek(day)
              .startTime(LocalTime.of(8, 30))
              .endTime(LocalTime.of(12, 30))
              .isAvailable(true)
              .build());
      slots.add(
          AvailabilityTemplate.TemplateSlot.builder()
              .type(AgentAvailability.SlotType.RECURRING)
              .dayOfWeek(day)
              .startTime(LocalTime.of(14, 30))
              .endTime(LocalTime.of(18, 30))
              .isAvailable(true)
              .build());
    }

    AvailabilityTemplate standardTemplate =
        AvailabilityTemplate.builder()
            .name("Horario de Oficina Estándar")
            .description("Lunes a Viernes, 08:30–12:30 y 14:30–18:30")
            .isStandard(true)
            .slots(slots)
            .build();

    templateRepository.save(standardTemplate);
    log.info("Horario de Oficina Estándar template seeded successfully.");

    // Clean up any existing AgentAvailability slots applied from "Horario de Oficina Estándar" or
    // "Standard Office Hours" that got
    // saved as false
    List<AgentAvailability> standardSlots =
        availabilityRepository.findAll().stream()
            .filter(
                a ->
                    a.getNotes() != null
                        && (a.getNotes().contains("Standard Office Hours")
                            || a.getNotes().contains("Horario de Oficina Estándar")))
            .toList();

    int fixedCount = 0;
    for (AgentAvailability a : standardSlots) {
      if (!a.isAvailable()) {
        a.setAvailable(true);
        availabilityRepository.save(a);
        fixedCount++;
      }
    }
    if (fixedCount > 0) {
      log.info(
          "Fixed {} existing agent availability slots that were incorrectly marked as unavailable.",
          fixedCount);
    }
  }

  public List<AgentAvailability> getAgentAvailability(String agentId) {
    return availabilityRepository.findByAgentId(agentId);
  }

  public AgentAvailability saveAvailability(String agentId, AgentAvailability availability) {
    availability.setAgentId(agentId);

    // Check overlaps with existing slots of same type (Recurring or Exception date)
    validateNoOverlap(availability);

    return availabilityRepository.save(availability);
  }

  public AgentAvailability updateAvailability(
      String agentId, String slotId, AgentAvailability availability) {
    AgentAvailability existing =
        availabilityRepository
            .findById(slotId)
            .orElseThrow(
                () -> new IllegalArgumentException("Availability slot not found: " + slotId));

    if (!existing.getAgentId().equals(agentId)) {
      throw new IllegalArgumentException("Slot does not belong to agent: " + agentId);
    }

    availability.setId(slotId);
    availability.setAgentId(agentId);

    validateNoOverlap(availability);

    return availabilityRepository.save(availability);
  }

  public void deleteAvailability(String agentId, String slotId) {
    AgentAvailability existing =
        availabilityRepository
            .findById(slotId)
            .orElseThrow(
                () -> new IllegalArgumentException("Availability slot not found: " + slotId));

    if (!existing.getAgentId().equals(agentId)) {
      throw new IllegalArgumentException("Slot does not belong to agent: " + agentId);
    }

    availabilityRepository.deleteById(slotId);
  }

  // Templates Management
  public AvailabilityTemplate createTemplate(AvailabilityTemplate template) {
    template.setStandard(false); // Only seed standard can be true
    return templateRepository.save(template);
  }

  public AvailabilityTemplate updateTemplate(String templateId, AvailabilityTemplate template) {
    AvailabilityTemplate existing =
        templateRepository
            .findById(templateId)
            .orElseThrow(
                () -> new IllegalArgumentException("Plantilla no encontrada: " + templateId));

    if (existing.isStandard()) {
      throw new IllegalArgumentException(
          "No se puede modificar la plantilla estándar del sistema.");
    }

    existing.setName(template.getName());
    existing.setDescription(template.getDescription());
    existing.setSlots(template.getSlots());

    return templateRepository.save(existing);
  }

  public List<AvailabilityTemplate> getTemplates() {
    return templateRepository.findAll();
  }

  public void deleteTemplate(String templateId) {
    AvailabilityTemplate template =
        templateRepository
            .findById(templateId)
            .orElseThrow(
                () -> new IllegalArgumentException("Plantilla no encontrada: " + templateId));

    if (template.isStandard()) {
      throw new IllegalArgumentException("No se puede eliminar la plantilla estándar del sistema.");
    }

    templateRepository.deleteById(templateId);
  }

  public void applyTemplate(String templateId, List<String> agentIds, boolean overwrite) {
    AvailabilityTemplate template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

    for (String agentId : agentIds) {
      if (overwrite) {
        availabilityRepository.deleteByAgentIdAndType(
            agentId, AgentAvailability.SlotType.RECURRING);
      }

      for (AvailabilityTemplate.TemplateSlot tSlot : template.getSlots()) {
        AgentAvailability avail =
            AgentAvailability.builder()
                .agentId(agentId)
                .type(tSlot.getType())
                .dayOfWeek(tSlot.getDayOfWeek())
                .specificDate(tSlot.getSpecificDate())
                .startTime(tSlot.getStartTime())
                .endTime(tSlot.getEndTime())
                .isAvailable(tSlot.isAvailable())
                .notes("Aplicado desde plantilla: " + template.getName())
                .build();

        try {
          availabilityRepository.save(avail);
        } catch (Exception e) {
          log.warn(
              "Could not save slot when applying template for agent {}: {}",
              agentId,
              e.getMessage());
        }
      }
    }
  }

  /**
   * Core validation engine. Checks if agent is available during [startTime, endTime] (UTC). Throws
   * ScheduleConflictException if agent is not available.
   */
  public void checkAgentAvailability(String agentId, Instant startTime, Instant endTime) {
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("Start time and end time are required");
    }

    ZonedDateTime startZdt = startTime.atZone(java.time.ZoneId.systemDefault());
    ZonedDateTime endZdt = endTime.atZone(java.time.ZoneId.systemDefault());

    LocalDate specificDate = startZdt.toLocalDate();
    LocalTime localStart = startZdt.toLocalTime();
    LocalTime localEnd = endZdt.toLocalTime();

    List<AgentAvailability> allAvails = availabilityRepository.findByAgentId(agentId);
    if (allAvails.isEmpty()) {
      // Si el agente no tiene horarios definidos, se considera disponible las 24 horas, los 7 días
      // de la semana.
      return;
    }

    // 1. Check exceptions first
    List<AgentAvailability> exceptions =
        allAvails.stream()
            .filter(
                a ->
                    a.getType() == AgentAvailability.SlotType.EXCEPTION
                        && specificDate.equals(a.getSpecificDate()))
            .toList();

    // Check for unavailable exceptions (e.g. Holidays, blocks) overlapping proposed interval
    boolean hasUnavailableOverlap =
        exceptions.stream()
            .filter(a -> !a.isAvailable())
            .anyMatch(a -> timesOverlap(localStart, localEnd, a.getStartTime(), a.getEndTime()));

    if (hasUnavailableOverlap) {
      throw new ScheduleConflictException(
          "El agente no está disponible el día "
              + specificDate
              + " en ese horario (Feriado o excepción de no-disponibilidad).");
    }

    // 2. Check working hours (special exceptions or recurring slots)
    List<AgentAvailability> workingExceptions =
        exceptions.stream().filter(AgentAvailability::isAvailable).toList();

    if (!workingExceptions.isEmpty()) {
      // If there are working exception slots on this date, proposed interval must be fully covered
      // by them
      boolean isFullyCoveredByException =
          workingExceptions.stream()
              .anyMatch(a -> timesCover(localStart, localEnd, a.getStartTime(), a.getEndTime()));

      if (!isFullyCoveredByException) {
        throw new ScheduleConflictException(
            "La fecha "
                + specificDate
                + " tiene un horario especial y el propuesto queda fuera de sus horas permitidas.");
      }
    } else {
      // Otherwise, check regular recurring working hours
      DayOfWeek dayOfWeek = startZdt.getDayOfWeek();
      List<AgentAvailability> recurring =
          allAvails.stream()
              .filter(
                  a ->
                      a.getType() == AgentAvailability.SlotType.RECURRING
                          && dayOfWeek.equals(a.getDayOfWeek()))
              .toList();

      if (recurring.isEmpty()) {
        throw new ScheduleConflictException(
            "El agente no tiene horas de trabajo registradas para el día "
                + translateDayOfWeek(dayOfWeek)
                + ".");
      }

      boolean isFullyCoveredByRecurring =
          recurring.stream()
              .filter(AgentAvailability::isAvailable)
              .anyMatch(a -> timesCover(localStart, localEnd, a.getStartTime(), a.getEndTime()));

      if (!isFullyCoveredByRecurring) {
        throw new ScheduleConflictException(
            "El horario seleccionado está fuera de las horas laborables permitidas para el agente el día "
                + translateDayOfWeek(dayOfWeek)
                + ".");
      }
    }
  }

  // Helpers
  private boolean timesOverlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
    return s1.isBefore(e2) && e1.isAfter(s2);
  }

  private boolean timesCover(
      LocalTime proposedStart, LocalTime proposedEnd, LocalTime slotStart, LocalTime slotEnd) {
    return (proposedStart.equals(slotStart) || proposedStart.isAfter(slotStart))
        && (proposedEnd.equals(slotEnd) || proposedEnd.isBefore(slotEnd));
  }

  private String translateDayOfWeek(DayOfWeek day) {
    if (day == null) return "";
    return switch (day) {
      case MONDAY -> "Lunes";
      case TUESDAY -> "Martes";
      case WEDNESDAY -> "Miércoles";
      case THURSDAY -> "Jueves";
      case FRIDAY -> "Viernes";
      case SATURDAY -> "Sábado";
      case SUNDAY -> "Domingo";
    };
  }

  private void validateNoOverlap(AgentAvailability newAvail) {
    List<AgentAvailability> existing = availabilityRepository.findByAgentId(newAvail.getAgentId());

    for (AgentAvailability old : existing) {
      // Exclude self if updating
      if (newAvail.getId() != null && newAvail.getId().equals(old.getId())) {
        continue;
      }

      if (newAvail.getType() == AgentAvailability.SlotType.RECURRING
          && old.getType() == AgentAvailability.SlotType.RECURRING) {
        if (newAvail.getDayOfWeek() == old.getDayOfWeek()) {
          if (timesOverlap(
              newAvail.getStartTime(),
              newAvail.getEndTime(),
              old.getStartTime(),
              old.getEndTime())) {
            throw new IllegalArgumentException(
                "El nuevo horario se solapa con un horario recurrente existente el "
                    + translateDayOfWeek(newAvail.getDayOfWeek()));
          }
        }
      } else if (newAvail.getType() == AgentAvailability.SlotType.EXCEPTION
          && old.getType() == AgentAvailability.SlotType.EXCEPTION) {
        if (newAvail.getSpecificDate().equals(old.getSpecificDate())) {
          if (timesOverlap(
              newAvail.getStartTime(),
              newAvail.getEndTime(),
              old.getStartTime(),
              old.getEndTime())) {
            throw new IllegalArgumentException(
                "El nuevo horario se solapa con una excepción existente para la fecha "
                    + newAvail.getSpecificDate());
          }
        }
      }
    }
  }
}

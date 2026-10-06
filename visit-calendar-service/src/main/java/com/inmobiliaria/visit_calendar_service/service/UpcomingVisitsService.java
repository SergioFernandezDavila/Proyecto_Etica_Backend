package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.model.AlertConfig;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.repository.VisitRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpcomingVisitsService {

  private final VisitRepository visitRepository;
  private final AlertConfigService alertConfigService;

  public List<Visit> getUpcomingVisitsForUser(String userId, Integer customMinutes) {
    AlertConfig config = alertConfigService.getConfig();
    int minutes;
    if (customMinutes != null && customMinutes > 0) {
      minutes = customMinutes;
    } else {
      minutes = config.getAnticipationMinutes(); // ← Cambiado de getAnticipationHours()
    }

    Instant now = Instant.now();
    Instant limit = now.plus(minutes, ChronoUnit.MINUTES); // ← Sumar minutos

    // Visitas donde el usuario es agente o propietario (simplificado: solo por agentId)
    return visitRepository.findByAgentIdAndStartTimeBetweenAndStatus(
        userId, now, limit, Visit.EventStatus.SCHEDULED);
  }
}

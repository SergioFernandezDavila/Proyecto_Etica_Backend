package com.inmobiliaria.visit_calendar_service.scheduler;

import com.inmobiliaria.visit_calendar_service.service.DailySummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryScheduler {

  private final DailySummaryService dailySummaryService;

  // Se ejecuta todos los días a las 08:00 AM hora local (Bolivia)
  @Scheduled(cron = "0 0 8 * * *", zone = "America/La_Paz")
  public void sendDailySummaries() {
    log.info("Ejecutando scheduler de resumen diario a las 08:00");
    dailySummaryService.sendDailySummaries();
  }
}

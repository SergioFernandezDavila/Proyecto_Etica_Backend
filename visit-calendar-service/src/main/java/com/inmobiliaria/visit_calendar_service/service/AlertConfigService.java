package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.model.AlertConfig;
import com.inmobiliaria.visit_calendar_service.repository.AlertConfigRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertConfigService {

  private final AlertConfigRepository repository;

  public AlertConfig getConfig() {
    return repository
        .findById("DEFAULT")
        .orElseGet(
            () -> {
              AlertConfig defaultConfig = new AlertConfig();
              defaultConfig.setId("DEFAULT");
              defaultConfig.setEnableIndividualReminders(true);
              defaultConfig.setAnticipationMinutes(60);
              defaultConfig.setEnableDailySummary(true);
              defaultConfig.setChannel("IN_APP");
              return repository.save(defaultConfig);
            });
  }

  public AlertConfig updateConfig(
      boolean enableDailySummary,
      boolean enableIndividualReminders,
      int anticipationMinutes,
      String channel) {
    AlertConfig config = getConfig();
    config.setEnableDailySummary(enableDailySummary);
    config.setEnableIndividualReminders(enableIndividualReminders);
    config.setAnticipationMinutes(anticipationMinutes);
    if (channel != null) config.setChannel(channel);
    return repository.save(config);
  }

  public void markDailyNotificationSent() {
    AlertConfig config = getConfig();
    config.setLastDailyNotificationDate(LocalDate.now());
    repository.save(config);
  }
}

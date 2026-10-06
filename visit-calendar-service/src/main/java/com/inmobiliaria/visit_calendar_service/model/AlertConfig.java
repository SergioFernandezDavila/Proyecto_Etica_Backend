package com.inmobiliaria.visit_calendar_service.model;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alert_config")
public class AlertConfig {
  @Id private String id = "DEFAULT";

  // Configuración para recordatorios individuales
  private boolean enableIndividualReminders = false;
  private int anticipationMinutes = 60; // 30, 60 o 90 minutos

  // Configuración para resumen diario
  private boolean enableDailySummary = true;
  private LocalDate lastDailyNotificationDate; // Para evitar duplicados en el día

  // Canal de notificación (reservado para futuro)
  private String channel = "IN_APP";
}

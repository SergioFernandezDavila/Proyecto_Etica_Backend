package com.inmobiliaria.visit_calendar_service.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Entidad que representa un registro de uso de un vehículo asociado a una visita. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vehicle_usage_records")
public class UsageRecord {

  @Id private String id;

  /** ID del vehículo utilizado */
  private String vehicleId;

  /** ID de la visita asociada */
  private String visitId;

  /** Fecha del uso (normalmente la fecha de la visita) */
  private Instant date;

  /** Duración del uso en horas */
  private double durationHours;

  /** Kilometraje registrado (en km) */
  private double mileage;
}

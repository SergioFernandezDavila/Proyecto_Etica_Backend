package com.inmobiliaria.visit_calendar_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Entidad que representa un Vehículo utilizado para las visitas inmobiliarias. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vehicles")
public class Vehicle {

  @Id private String id;

  /** Matrícula del vehículo */
  private String licensePlate;

  /** Marca del vehículo */
  private String brand;

  /** Modelo del vehículo */
  private String model;

  /** Capacidad de pasajeros */
  private int passengerCapacity;

  /** Estado del vehículo: available / in_use / maintenance */
  private VehicleStatus status;

  public enum VehicleStatus {
    AVAILABLE, // available
    IN_USE, // in_use
    MAINTENANCE // maintenance
  }
}

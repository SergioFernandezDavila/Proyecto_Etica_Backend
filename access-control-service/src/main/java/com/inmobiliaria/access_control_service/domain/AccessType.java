package com.inmobiliaria.access_control_service.domain;

public enum AccessType {
  MAC(
      "Mandatory Access Control",
      "Función / Área general",
      "Acceso determinado por la política central y el área o función general del usuario"),
  RBAC(
      "Role-Based Access Control",
      "Segregación / Restricción de funciones",
      "Acceso determinado por el rol asignado y la segregación de deberes y responsabilidades"),
  DAC(
      "Discretionary Access Control",
      "Temporal / Puntual",
      "Acceso discrecional asignado de forma temporal o puntual por delegación específica");

  private final String label;
  private final String theoreticalDefinition;
  private final String description;

  AccessType(String label, String theoreticalDefinition, String description) {
    this.label = label;
    this.theoreticalDefinition = theoreticalDefinition;
    this.description = description;
  }

  public String getLabel() {
    return label;
  }

  public String getTheoreticalDefinition() {
    return theoreticalDefinition;
  }

  public String getDescription() {
    return description;
  }
}

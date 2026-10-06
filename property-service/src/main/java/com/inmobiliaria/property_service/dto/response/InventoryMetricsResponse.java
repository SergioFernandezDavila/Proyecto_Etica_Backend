package com.inmobiliaria.property_service.dto.response;

import java.util.Map;

public record InventoryMetricsResponse(
    // Estadísticas generales
    int totalPropertiesAnalyzed,
    int totalSoldProperties,
    int totalRetiredProperties,
    int totalActiveProperties,

    // Tiempo en inventario (en días)
    double averageDaysInInventory,
    double medianDaysInInventory,
    double percentile25,
    double percentile75,
    double percentile95,
    double minDaysInInventory,
    double maxDaysInInventory,

    // Desglose por tipo de inmueble
    Map<String, TypeMetrics> metricsByPropertyType,

    // Desglose por operación (venta/alquiler/anticretico)
    Map<String, OperationMetrics> metricsByOperationType,

    // Desglose por zona geográfica
    Map<String, ZoneMetrics> metricsByZone,

    // Filtros aplicados
    String operationTypeFilter,
    String zoneFilter,
    String propertyTypeFilter) {

  // Inner record para métricas por tipo de inmueble
  public record TypeMetrics(
      int count,
      int soldCount,
      int retiredCount,
      double averageDays,
      double medianDays,
      double percentile75Days) {}

  // Inner record para métricas por tipo de operación
  public record OperationMetrics(
      int count,
      int soldCount,
      int retiredCount,
      double averageDays,
      double medianDays,
      double percentile75Days) {}

  // Inner record para métricas por zona
  public record ZoneMetrics(
      int count,
      int soldCount,
      int retiredCount,
      double averageDays,
      double medianDays,
      double percentile75Days) {}
}

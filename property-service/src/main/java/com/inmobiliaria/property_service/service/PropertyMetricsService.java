package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.domain.PropertyStatus;
import com.inmobiliaria.property_service.domain.StatusHistory;
import com.inmobiliaria.property_service.dto.response.InventoryMetricsResponse;
import com.inmobiliaria.property_service.dto.response.InventoryMetricsResponse.OperationMetrics;
import com.inmobiliaria.property_service.dto.response.InventoryMetricsResponse.TypeMetrics;
import com.inmobiliaria.property_service.dto.response.InventoryMetricsResponse.ZoneMetrics;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Servicio para calcular métricas de tiempo en inventario para propiedades. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyMetricsService {

  private final PropertyRepository propertyRepository;

  /**
   * Calcula las métricas de tiempo en inventario para todas las propiedades o filtradas.
   *
   * @param operationTypeFilter Filtro opcional por tipo de operación (VENTA, ALQUILER, ANTICRETICO)
   * @param zoneFilter Filtro opcional por zona geográfica
   * @param propertyTypeFilter Filtro opcional por tipo de inmueble
   * @return InventoryMetricsResponse con todas las métricas calculadas
   */
  public InventoryMetricsResponse calculateInventoryMetrics(
      String operationTypeFilter, String zoneFilter, String propertyTypeFilter) {

    log.info(
        "Calculating inventory metrics with filters - operationType: {}, zone: {}, propertyType: {}",
        operationTypeFilter,
        zoneFilter,
        propertyTypeFilter);

    // Obtener todas las propiedades no eliminadas
    List<PropertyDocument> allProperties =
        propertyRepository.findByDeletedFalse().stream()
            .filter(p -> p.getStatus() != PropertyStatus.ELIMINADO)
            .collect(Collectors.toList());

    // Aplicar filtros
    List<PropertyDocument> filteredProperties =
        applyFilters(allProperties, operationTypeFilter, zoneFilter, propertyTypeFilter);

    log.info(
        "Analyzing {} properties out of {} total", filteredProperties.size(), allProperties.size());

    // Calcular días en inventario para cada propiedad
    Map<PropertyDocument, Double> daysInInventoryMap = new HashMap<>();
    List<Double> allDaysInInventory = new ArrayList<>();

    for (PropertyDocument property : filteredProperties) {
      Double daysInInventory = calculateDaysInInventory(property);
      if (daysInInventory != null && daysInInventory >= 0) {
        daysInInventoryMap.put(property, daysInInventory);
        // Sólo incluir en el promedio de tiempo si el inmueble fue VENDIDO
        if (property.getStatus() == PropertyStatus.VENDIDO) {
          allDaysInInventory.add(daysInInventory);
        }
      }
    }

    // Calcular estadísticas generales
    int totalPropertiesAnalyzed = filteredProperties.size();
    int totalSoldProperties =
        (int)
            filteredProperties.stream()
                .filter(p -> p.getStatus() == PropertyStatus.VENDIDO)
                .count();
    int totalRetiredProperties =
        (int)
            filteredProperties.stream()
                .filter(p -> p.getStatus() == PropertyStatus.RETIRADO)
                .count();
    int totalActiveProperties =
        totalPropertiesAnalyzed - totalSoldProperties - totalRetiredProperties;

    // Calcular percentiles y estadísticas
    double averageDaysInInventory = calculateAverage(allDaysInInventory);
    double medianDaysInInventory = calculateMedian(allDaysInInventory);
    double percentile25 = calculatePercentile(allDaysInInventory, 0.25);
    double percentile75 = calculatePercentile(allDaysInInventory, 0.75);
    double percentile95 = calculatePercentile(allDaysInInventory, 0.95);
    double minDaysInInventory =
        allDaysInInventory.isEmpty() ? 0 : Collections.min(allDaysInInventory);
    double maxDaysInInventory =
        allDaysInInventory.isEmpty() ? 0 : Collections.max(allDaysInInventory);

    // Desglose por tipo de inmueble
    Map<String, TypeMetrics> metricsByPropertyType =
        calculateMetricsByPropertyType(filteredProperties, daysInInventoryMap);

    // Desglose por tipo de operación
    Map<String, OperationMetrics> metricsByOperationType =
        calculateMetricsByOperationType(filteredProperties, daysInInventoryMap);

    // Desglose por zona
    Map<String, ZoneMetrics> metricsByZone =
        calculateMetricsByZone(filteredProperties, daysInInventoryMap);

    return new InventoryMetricsResponse(
        totalPropertiesAnalyzed,
        totalSoldProperties,
        totalRetiredProperties,
        totalActiveProperties,
        averageDaysInInventory,
        medianDaysInInventory,
        percentile25,
        percentile75,
        percentile95,
        minDaysInInventory,
        maxDaysInInventory,
        metricsByPropertyType,
        metricsByOperationType,
        metricsByZone,
        operationTypeFilter != null ? operationTypeFilter : "TODOS",
        zoneFilter != null ? zoneFilter : "TODAS",
        propertyTypeFilter != null ? propertyTypeFilter : "TODOS");
  }

  /**
   * Calcula los días que una propiedad ha pasado en el inventario. Si está vendida o retirada,
   * calcula desde createdAt hasta el cambio de estado. Si aún está activa, retorna null.
   *
   * @param property La propiedad a analizar
   * @return Número de días en inventario, o null si no aplica
   */
  private Double calculateDaysInInventory(PropertyDocument property) {
    // Solo considerar propiedades VENDIDAS o RETIRADAS
    if (property.getStatus() != PropertyStatus.VENDIDO
        && property.getStatus() != PropertyStatus.RETIRADO) {
      return null;
    }

    Instant startDate = property.getCreatedAt();
    if (startDate == null) {
      return null;
    }

    // Buscar el momento en que se cambió a VENDIDO o RETIRADO
    Instant endDate = findStatusChangeDate(property, property.getStatus());

    if (endDate == null) {
      // Si no hay registro del cambio, usar updatedAt
      endDate = property.getUpdatedAt();
    }

    if (endDate == null) {
      return null;
    }

    // Calcular días entre las dos fechas
    long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
    return (double) daysBetween;
  }

  /**
   * Busca la fecha en que una propiedad cambió a un estado específico usando el statusHistory.
   *
   * @param property La propiedad
   * @param targetStatus El estado buscado
   * @return La fecha del cambio, o null si no se encuentra
   */
  private Instant findStatusChangeDate(PropertyDocument property, PropertyStatus targetStatus) {
    if (property.getStatusHistory() == null || property.getStatusHistory().isEmpty()) {
      return null;
    }

    // Buscar el cambio más reciente a ese estado
    return property.getStatusHistory().stream()
        .filter(sh -> sh.getNewStatus().equals(targetStatus.name()))
        .max(Comparator.comparing(StatusHistory::getChangedAt))
        .map(StatusHistory::getChangedAt)
        .orElse(null);
  }

  /**
   * Aplica filtros a la lista de propiedades.
   *
   * @param properties Lista de propiedades a filtrar
   * @param operationTypeFilter Tipo de operación (opcional)
   * @param zoneFilter Zona geográfica (opcional)
   * @param propertyTypeFilter Tipo de inmueble (opcional)
   * @return Lista filtrada de propiedades
   */
  private List<PropertyDocument> applyFilters(
      List<PropertyDocument> properties,
      String operationTypeFilter,
      String zoneFilter,
      String propertyTypeFilter) {

    return properties.stream()
        .filter(
            p ->
                operationTypeFilter == null
                    || (p.getOperationType() != null
                        && p.getOperationType().name().equals(operationTypeFilter.toUpperCase())))
        .filter(p -> zoneFilter == null || (p.getZone() != null && p.getZone().equals(zoneFilter)))
        .filter(
            p ->
                propertyTypeFilter == null
                    || (p.getType() != null && p.getType().equals(propertyTypeFilter)))
        .collect(Collectors.toList());
  }

  /**
   * Calcula métricas agrupadas por tipo de inmueble.
   *
   * @param properties Lista de propiedades
   * @param daysInInventoryMap Mapa de días en inventario por propiedad
   * @return Mapa de métricas por tipo de inmueble
   */
  private Map<String, TypeMetrics> calculateMetricsByPropertyType(
      List<PropertyDocument> properties, Map<PropertyDocument, Double> daysInInventoryMap) {

    Map<String, List<PropertyDocument>> groupedByType =
        properties.stream()
            .collect(
                Collectors.groupingBy(p -> p.getType() != null ? p.getType() : "Sin especificar"));

    Map<String, TypeMetrics> metrics = new HashMap<>();

    for (Map.Entry<String, List<PropertyDocument>> entry : groupedByType.entrySet()) {
      String type = entry.getKey();
      List<PropertyDocument> typeProperties = entry.getValue();

      List<Double> daysForType = new ArrayList<>();
      int soldCount = 0;
      int retiredCount = 0;

      for (PropertyDocument prop : typeProperties) {
        if (daysInInventoryMap.containsKey(prop) && prop.getStatus() == PropertyStatus.VENDIDO) {
          daysForType.add(daysInInventoryMap.get(prop));
        }
        if (prop.getStatus() == PropertyStatus.VENDIDO) soldCount++;
        if (prop.getStatus() == PropertyStatus.RETIRADO) retiredCount++;
      }

      metrics.put(
          type,
          new TypeMetrics(
              typeProperties.size(),
              soldCount,
              retiredCount,
              calculateAverage(daysForType),
              calculateMedian(daysForType),
              calculatePercentile(daysForType, 0.75)));
    }

    return metrics;
  }

  /**
   * Calcula métricas agrupadas por tipo de operación.
   *
   * @param properties Lista de propiedades
   * @param daysInInventoryMap Mapa de días en inventario por propiedad
   * @return Mapa de métricas por tipo de operación
   */
  private Map<String, OperationMetrics> calculateMetricsByOperationType(
      List<PropertyDocument> properties, Map<PropertyDocument, Double> daysInInventoryMap) {

    Map<String, List<PropertyDocument>> groupedByOperation =
        properties.stream()
            .collect(
                Collectors.groupingBy(
                    p ->
                        p.getOperationType() != null
                            ? p.getOperationType().name()
                            : "Sin especificar"));

    Map<String, OperationMetrics> metrics = new HashMap<>();

    for (Map.Entry<String, List<PropertyDocument>> entry : groupedByOperation.entrySet()) {
      String operation = entry.getKey();
      List<PropertyDocument> operationProperties = entry.getValue();

      List<Double> daysForOperation = new ArrayList<>();
      int soldCount = 0;
      int retiredCount = 0;

      for (PropertyDocument prop : operationProperties) {
        if (daysInInventoryMap.containsKey(prop) && prop.getStatus() == PropertyStatus.VENDIDO) {
          daysForOperation.add(daysInInventoryMap.get(prop));
        }
        if (prop.getStatus() == PropertyStatus.VENDIDO) soldCount++;
        if (prop.getStatus() == PropertyStatus.RETIRADO) retiredCount++;
      }

      metrics.put(
          operation,
          new OperationMetrics(
              operationProperties.size(),
              soldCount,
              retiredCount,
              calculateAverage(daysForOperation),
              calculateMedian(daysForOperation),
              calculatePercentile(daysForOperation, 0.75)));
    }

    return metrics;
  }

  /**
   * Calcula métricas agrupadas por zona geográfica.
   *
   * @param properties Lista de propiedades
   * @param daysInInventoryMap Mapa de días en inventario por propiedad
   * @return Mapa de métricas por zona
   */
  private Map<String, ZoneMetrics> calculateMetricsByZone(
      List<PropertyDocument> properties, Map<PropertyDocument, Double> daysInInventoryMap) {

    Map<String, List<PropertyDocument>> groupedByZone =
        properties.stream()
            .collect(
                Collectors.groupingBy(p -> p.getZone() != null ? p.getZone() : "Sin especificar"));

    Map<String, ZoneMetrics> metrics = new HashMap<>();

    for (Map.Entry<String, List<PropertyDocument>> entry : groupedByZone.entrySet()) {
      String zone = entry.getKey();
      List<PropertyDocument> zoneProperties = entry.getValue();

      List<Double> daysForZone = new ArrayList<>();
      int soldCount = 0;
      int retiredCount = 0;

      for (PropertyDocument prop : zoneProperties) {
        if (daysInInventoryMap.containsKey(prop) && prop.getStatus() == PropertyStatus.VENDIDO) {
          daysForZone.add(daysInInventoryMap.get(prop));
        }
        if (prop.getStatus() == PropertyStatus.VENDIDO) soldCount++;
        if (prop.getStatus() == PropertyStatus.RETIRADO) retiredCount++;
      }

      metrics.put(
          zone,
          new ZoneMetrics(
              zoneProperties.size(),
              soldCount,
              retiredCount,
              calculateAverage(daysForZone),
              calculateMedian(daysForZone),
              calculatePercentile(daysForZone, 0.75)));
    }

    return metrics;
  }

  /**
   * Calcula el promedio de una lista de números.
   *
   * @param values Lista de valores
   * @return Promedio, o 0.0 si la lista está vacía
   */
  private double calculateAverage(List<Double> values) {
    if (values.isEmpty()) {
      return 0.0;
    }
    return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /**
   * Calcula la mediana de una lista de números.
   *
   * @param values Lista de valores
   * @return Mediana, o 0.0 si la lista está vacía
   */
  private double calculateMedian(List<Double> values) {
    if (values.isEmpty()) {
      return 0.0;
    }

    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);

    int size = sorted.size();
    if (size % 2 == 0) {
      return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    } else {
      return sorted.get(size / 2);
    }
  }

  /**
   * Calcula un percentil de una lista de números.
   *
   * @param values Lista de valores
   * @param percentile Percentil a calcular (entre 0 y 1, ej: 0.75 para P75)
   * @return El valor del percentil, o 0.0 si la lista está vacía
   */
  private double calculatePercentile(List<Double> values, double percentile) {
    if (values.isEmpty()) {
      return 0.0;
    }

    if (percentile < 0 || percentile > 1) {
      throw new IllegalArgumentException("Percentile must be between 0 and 1");
    }

    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);

    int size = sorted.size();
    double index = percentile * (size - 1);
    int lower = (int) Math.floor(index);
    int upper = (int) Math.ceil(index);

    if (lower == upper) {
      return sorted.get(lower);
    }

    double weight = index - lower;
    return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
  }
}

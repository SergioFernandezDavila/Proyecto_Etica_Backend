package com.inmobiliaria.property_service.dto.response;

import java.util.List;
import java.util.Map;

public record InventoryReportResponse(
    Map<String, Long> totalsByStatus,
    Map<String, Long> totalsByOperationType,
    List<PropertyReportItem> properties) {
  public record PropertyReportItem(
      String id,
      String title,
      String status,
      String operationType,
      Double price,
      String zone,
      long daysInInventory,
      String registrationDate,
      String exitDate) {}
}

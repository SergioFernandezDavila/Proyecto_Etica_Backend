package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.dto.UsageRecordDetailDTO;
import com.inmobiliaria.visit_calendar_service.dto.VehicleUsageReportResponse;
import com.inmobiliaria.visit_calendar_service.dto.VehicleUsageSummaryDTO;
import com.inmobiliaria.visit_calendar_service.model.UsageRecord;
import com.inmobiliaria.visit_calendar_service.model.Vehicle;
import com.inmobiliaria.visit_calendar_service.model.Visit;
import com.inmobiliaria.visit_calendar_service.repository.UsageRecordRepository;
import com.inmobiliaria.visit_calendar_service.repository.VehicleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleUsageService {

  private final UsageRecordRepository usageRecordRepository;
  private final VehicleRepository vehicleRepository;

  /** Registra el uso de un vehículo asociado a una visita completada. */
  public void recordUsage(Visit visit, Double mileage) {
    if (visit.getVehicleId() == null) {
      log.debug("La visita {} no tiene vehículo asignado, saltando registro de uso", visit.getId());
      return;
    }

    // Calcular duración: (endTime - startTime) + travelTimeGo + travelTimeBack
    long visitDurationMinutes =
        Duration.between(visit.getStartTime(), visit.getEndTime()).toMinutes();
    int totalTravelMinutes =
        (visit.getTravelTimeGo() != null ? visit.getTravelTimeGo() : 0)
            + (visit.getTravelTimeBack() != null ? visit.getTravelTimeBack() : 0);

    double totalHours = (visitDurationMinutes + totalTravelMinutes) / 60.0;

    UsageRecord record =
        UsageRecord.builder()
            .vehicleId(visit.getVehicleId())
            .visitId(visit.getId())
            .date(visit.getStartTime())
            .durationHours(totalHours)
            .mileage(mileage != null ? mileage : 0.0)
            .build();

    usageRecordRepository.save(record);
    log.info(
        "Registro de uso creado para vehículo {} en visita {}",
        visit.getVehicleId(),
        visit.getId());
  }

  public VehicleUsageReportResponse generateReport(String vehicleId, Instant from, Instant to) {
    List<UsageRecord> records;
    if (vehicleId != null && !vehicleId.isBlank()) {
      records = usageRecordRepository.findByVehicleIdAndDateBetween(vehicleId, from, to);
    } else {
      records = usageRecordRepository.findByDateBetween(from, to);
    }

    List<Vehicle> allVehicles = vehicleRepository.findAll();
    Map<String, List<UsageRecord>> recordsByVehicle =
        records.stream().collect(Collectors.groupingBy(UsageRecord::getVehicleId));

    List<VehicleUsageSummaryDTO> summaries = new ArrayList<>();

    for (Vehicle v : allVehicles) {
      // Si se filtró por un vehículo específico, ignorar los demás
      if (vehicleId != null && !vehicleId.isBlank() && !v.getId().equals(vehicleId)) {
        continue;
      }

      List<UsageRecord> vRecords = recordsByVehicle.getOrDefault(v.getId(), List.of());

      double totalHours = vRecords.stream().mapToDouble(UsageRecord::getDurationHours).sum();
      double totalMileage = vRecords.stream().mapToDouble(UsageRecord::getMileage).sum();

      List<UsageRecordDetailDTO> details =
          vRecords.stream()
              .map(
                  r ->
                      UsageRecordDetailDTO.builder()
                          .visitId(r.getVisitId())
                          .date(r.getDate())
                          .durationHours(r.getDurationHours())
                          .mileage(r.getMileage())
                          .build())
              .collect(Collectors.toList());

      summaries.add(
          VehicleUsageSummaryDTO.builder()
              .vehicleId(v.getId())
              .licensePlate(v.getLicensePlate())
              .brand(v.getBrand())
              .model(v.getModel())
              .totalHours(totalHours)
              .visitCount(vRecords.size())
              .totalMileage(totalMileage)
              .details(details)
              .build());
    }

    return VehicleUsageReportResponse.builder().from(from).to(to).vehicles(summaries).build();
  }

  public List<UsageRecord> getUsageByVehicleAndPeriod(String vehicleId, Instant from, Instant to) {
    return usageRecordRepository.findByVehicleIdAndDateBetween(vehicleId, from, to);
  }

  public List<UsageRecord> getUsageByPeriod(Instant from, Instant to) {
    return usageRecordRepository.findByDateBetween(from, to);
  }
}

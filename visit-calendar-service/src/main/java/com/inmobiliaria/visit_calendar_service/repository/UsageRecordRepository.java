package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.UsageRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsageRecordRepository extends MongoRepository<UsageRecord, String> {
  List<UsageRecord> findByVehicleIdAndDateBetween(String vehicleId, Instant start, Instant end);

  List<UsageRecord> findByDateBetween(Instant start, Instant end);
}

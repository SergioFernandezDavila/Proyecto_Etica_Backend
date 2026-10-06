package com.inmobiliaria.operation_service.client;

import com.inmobiliaria.operation_service.config.FeignConfig;
import com.inmobiliaria.operation_service.dto.response.ApiResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "visit-calendar-service", configuration = FeignConfig.class)
public interface VisitClient {

  @GetMapping("/calendar")
  ApiResponse<CalendarResponseDto> getCalendar(
      @RequestParam("from") Instant from, @RequestParam("to") Instant to);

  record CalendarResponseDto(List<Object> events, int totalEvents) {}
}

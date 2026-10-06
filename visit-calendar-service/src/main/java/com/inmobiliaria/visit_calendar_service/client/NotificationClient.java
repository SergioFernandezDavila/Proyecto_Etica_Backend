package com.inmobiliaria.visit_calendar_service.client;

import com.inmobiliaria.visit_calendar_service.dto.SendNotificationRequest;
import com.inmobiliaria.visit_calendar_service.dto.request.SendInAppNotificationRequest;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service")
public interface NotificationClient {

  @PostMapping("/notifications/send")
  ResponseEntity<Void> sendNotification(@RequestBody SendNotificationRequest request);

  // Note: These specific endpoints are called by legacy code but might not exist in
  // notification-service.
  // They are kept for compatibility during migration.
  @PostMapping("/notifications/visit-request")
  ResponseEntity<String> notifyVisitRequest(@RequestBody Map<String, Object> payload);

  @PostMapping("/notifications/reassignment-request")
  ResponseEntity<String> notifyReassignmentRequest(@RequestBody Map<String, Object> payload);

  @PostMapping("/notifications/reassignment-decision")
  ResponseEntity<String> notifyReassignmentDecision(@RequestBody Map<String, Object> payload);

  @PostMapping("/notifications/in-app")
  void sendInAppNotification(@RequestBody SendInAppNotificationRequest request);
}

package com.inmobiliaria.user_service.client;

import com.inmobiliaria.user_service.config.NotificationFeignConfig;
import com.inmobiliaria.user_service.dto.request.SendInAppNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", configuration = NotificationFeignConfig.class)
public interface NotificationClient {

  @PostMapping("/notifications/in-app")
  void sendInAppNotification(@RequestBody SendInAppNotificationRequest request);
}

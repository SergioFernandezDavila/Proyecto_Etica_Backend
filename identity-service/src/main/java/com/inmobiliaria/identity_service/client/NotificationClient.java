package com.inmobiliaria.identity_service.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service")
public interface NotificationClient {

  @PostMapping("/notifications/credentials")
  void sendCredentialsEmail(@RequestBody CredentialsRequest request);

  @PostMapping("/notifications/mfa")
  void sendMfaEmail(@RequestBody MfaEmailRequest request);

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  class CredentialsRequest {
    private String to;
    private String fullName;
    private String temporaryPassword;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  class MfaEmailRequest {
    private String to;
    private String fullName;
    private String code;
  }
}

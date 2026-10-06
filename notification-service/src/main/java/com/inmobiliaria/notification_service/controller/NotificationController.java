package com.inmobiliaria.notification_service.controller;

import com.inmobiliaria.notification_service.domain.NotificationDocument;
import com.inmobiliaria.notification_service.domain.NotificationStatus;
import com.inmobiliaria.notification_service.dto.request.SendAttachmentEmailRequest;
import com.inmobiliaria.notification_service.dto.request.SendCredentialsEmailRequest;
import com.inmobiliaria.notification_service.dto.request.SendInAppNotificationRequest;
import com.inmobiliaria.notification_service.dto.request.SendNotificationRequest;
import com.inmobiliaria.notification_service.dto.response.ApiResponse;
import com.inmobiliaria.notification_service.dto.response.InAppNotificationResponse;
import com.inmobiliaria.notification_service.dto.response.NotificationHistoryResponse;
import com.inmobiliaria.notification_service.dto.response.NotificationResponse;
import com.inmobiliaria.notification_service.dto.response.ResponseFactory;
import com.inmobiliaria.notification_service.repository.NotificationRepository;
import com.inmobiliaria.notification_service.service.NotificationDispatcher;
import com.inmobiliaria.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;
  private final ResponseFactory responseFactory;
  private final NotificationRepository notificationRepository;
  private final NotificationDispatcher notificationDispatcher;

  @PostMapping("/credentials")
  public ResponseEntity<ApiResponse<NotificationResponse>> sendCredentials(
      @Valid @RequestBody SendCredentialsEmailRequest request) {
    NotificationResponse response = notificationService.sendCredentialsEmail(request);
    return ResponseEntity.ok(
        responseFactory.success("Credentials email sent successfully", response));
  }

  @PostMapping("/send-attachment")
  public ResponseEntity<ApiResponse<Void>> sendNotificationWithAttachment(
      @Valid @RequestBody SendAttachmentEmailRequest req) {
    notificationService.sendEmailWithAttachment(req);
    return ResponseEntity.ok(responseFactory.success("Email with attachment sent successfully"));
  }

  // controller/NotificationController.java
  @PostMapping("/send")
  public ResponseEntity<ApiResponse<Void>> sendNotification(
      @Valid @RequestBody SendNotificationRequest req) {
    NotificationDocument doc =
        NotificationDocument.builder()
            .recipientId(req.recipientId())
            .type(req.type())
            .channel(req.channel() != null ? req.channel() : "EMAIL")
            .subject(req.subject())
            .content(req.content())
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .build();
    notificationDispatcher.send(doc); // asíncrono
    return ResponseEntity.accepted().body(responseFactory.success("Notificación encolada"));
  }

  @GetMapping("/propietarios/{ownerId}/notificaciones")
  public ResponseEntity<ApiResponse<List<NotificationHistoryResponse>>> getNotificationsByOwner(
      @PathVariable String ownerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<NotificationDocument> pageResult =
        notificationRepository.findByRecipientId(ownerId, PageRequest.of(page, size));
    List<NotificationHistoryResponse> list = pageResult.map(this::toHistoryResponse).toList();
    return ResponseEntity.ok(
        responseFactory.paginated(
            "Historial de notificaciones", list, page, size, pageResult.getTotalElements()));
  }

  @PostMapping("/in-app")
  public ResponseEntity<ApiResponse<NotificationResponse>> sendInAppNotification(
      @Valid @RequestBody SendInAppNotificationRequest request) {
    NotificationResponse response = notificationService.sendInAppNotification(request);
    return ResponseEntity.accepted()
        .body(responseFactory.success("In-app notification queued", response));
  }

  @GetMapping("/in-app")
  public ResponseEntity<ApiResponse<List<InAppNotificationResponse>>> getUserInAppNotifications(
      @RequestParam(required = false) Boolean readStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestHeader("X-Auth-User-Id") String userId) {

    Page<InAppNotificationResponse> result =
        notificationService.getUserInAppNotifications(
            userId,
            readStatus,
            PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

    return ResponseEntity.ok(
        responseFactory.paginated(
            "In-app notifications retrieved",
            result.getContent(),
            page,
            pageSize,
            result.getTotalElements()));
  }

  @PutMapping("/in-app/{id}/read")
  public ResponseEntity<ApiResponse<Void>> markAsRead(
      @PathVariable String id, @RequestHeader("X-Auth-User-Id") String userId) {
    notificationService.markAsRead(id, userId);
    return ResponseEntity.ok(responseFactory.success("Notification marked as read", null));
  }

  private NotificationHistoryResponse toHistoryResponse(NotificationDocument doc) {
    LocalDateTime sentAtLocal =
        (doc.getSentAt() != null)
            ? LocalDateTime.ofInstant(doc.getSentAt(), java.time.ZoneOffset.UTC)
            : null;
    return new NotificationHistoryResponse(
        doc.getId(),
        doc.getType(),
        doc.getSubject(),
        doc.getContent(),
        doc.getStatus(),
        doc.getCreatedAt(),
        sentAtLocal,
        doc.getErrorMessage());
  }
}

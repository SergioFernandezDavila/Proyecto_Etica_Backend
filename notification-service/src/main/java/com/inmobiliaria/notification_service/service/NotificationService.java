package com.inmobiliaria.notification_service.service;

import com.inmobiliaria.notification_service.config.MailPropertiesConfig;
import com.inmobiliaria.notification_service.domain.EmailLogDocument;
import com.inmobiliaria.notification_service.domain.NotificationDocument;
import com.inmobiliaria.notification_service.domain.NotificationStatus;
import com.inmobiliaria.notification_service.dto.request.SendAttachmentEmailRequest;
import com.inmobiliaria.notification_service.dto.request.SendCredentialsEmailRequest;
import com.inmobiliaria.notification_service.dto.request.SendInAppNotificationRequest;
import com.inmobiliaria.notification_service.dto.response.InAppNotificationResponse;
import com.inmobiliaria.notification_service.dto.response.NotificationResponse;
import com.inmobiliaria.notification_service.exception.AccessDeniedException;
import com.inmobiliaria.notification_service.exception.EmailSendException;
import com.inmobiliaria.notification_service.exception.ResourceNotFoundException;
import com.inmobiliaria.notification_service.repository.EmailLogRepository;
import com.inmobiliaria.notification_service.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

  private final JavaMailSender mailSender;
  private final MailPropertiesConfig mailPropertiesConfig;
  private final EmailLogRepository emailLogRepository;
  private final NotificationRepository notificationRepository;
  private final NotificationDispatcher notificationDispatcher;

  public NotificationResponse sendCredentialsEmail(SendCredentialsEmailRequest request) {
    String subject = "Credenciales temporales de acceso";
    String body =
        buildCredentialsBody(request.fullName(), request.to(), request.temporaryPassword());

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(mailPropertiesConfig.getFrom());
      message.setTo(request.to());
      message.setSubject(subject);
      message.setText(body);

      mailSender.send(message);

      emailLogRepository.save(
          EmailLogDocument.builder()
              .to(request.to())
              .subject(subject)
              .body(body)
              .status(NotificationStatus.SENT)
              .errorMessage(null)
              .createdAt(Instant.now())
              .build());

      return new NotificationResponse("Email sent successfully", NotificationStatus.SENT);

    } catch (Exception ex) {
      emailLogRepository.save(
          EmailLogDocument.builder()
              .to(request.to())
              .subject(subject)
              .body(body)
              .status(NotificationStatus.FAILED)
              .errorMessage(ex.getMessage())
              .createdAt(Instant.now())
              .build());

      throw new EmailSendException("Failed to send email to " + request.to(), ex);
    }
  }

  public void sendEmailWithAttachment(SendAttachmentEmailRequest req) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

      helper.setFrom(mailPropertiesConfig.getFrom());
      helper.setTo(req.recipientId());
      helper.setSubject(req.subject());
      helper.setText(req.content(), false); // plain text body

      byte[] attachmentBytes = Base64.getDecoder().decode(req.attachmentBase64());
      ByteArrayResource resource = new ByteArrayResource(attachmentBytes);
      helper.addAttachment(req.attachmentFilename(), resource, "application/pdf");

      mailSender.send(mimeMessage);

      emailLogRepository.save(
          EmailLogDocument.builder()
              .to(req.recipientId())
              .subject(req.subject())
              .body(req.content())
              .status(NotificationStatus.SENT)
              .createdAt(Instant.now())
              .build());
    } catch (Exception ex) {
      emailLogRepository.save(
          EmailLogDocument.builder()
              .to(req.recipientId())
              .subject(req.subject())
              .body(req.content())
              .status(NotificationStatus.FAILED)
              .errorMessage(ex.getMessage())
              .createdAt(Instant.now())
              .build());
      throw new EmailSendException(
          "Failed to send email with attachment to " + req.recipientId(), ex);
    }
  }

  // NotificationService.java (añadir)
  public NotificationResponse sendInAppNotification(SendInAppNotificationRequest request) {
    NotificationDocument doc =
        NotificationDocument.builder()
            .recipientId(request.recipientId())
            .type(request.type())
            .channel("IN_APP")
            .subject(request.subject() != null ? request.subject() : "")
            .content(request.content())
            .interactionType(request.interactionType())
            .involvedUserIds(request.involvedUserIds())
            .details(request.details())
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .deliveredAt(null) // se seteará en el dispatcher
            .readStatus(false)
            .build();

    notificationDispatcher.send(doc); // asíncrono, marcará deliveredAt y status=SENT
    return new NotificationResponse("In-app notification queued", NotificationStatus.PENDING);
  }

  public Page<InAppNotificationResponse> getUserInAppNotifications(
      String userId, Boolean readStatus, Pageable pageable) {
    Page<NotificationDocument> page;
    if (readStatus != null) {
      page = notificationRepository.findByRecipientIdAndReadStatus(userId, readStatus, pageable);
    } else {
      page =
          notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(
              userId, "IN_APP", pageable);
    }
    return page.map(this::toInAppResponse);
  }

  public void markAsRead(String notificationId, String userId) {
    NotificationDocument doc =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
    if (!doc.getRecipientId().equals(userId)) {
      throw new AccessDeniedException("Cannot mark another user's notification");
    }
    if (!doc.isReadStatus()) {
      doc.setReadStatus(true);
      doc.setReadAt(Instant.now());
      notificationRepository.save(doc);
    }
  }

  private InAppNotificationResponse toInAppResponse(NotificationDocument doc) {
    return new InAppNotificationResponse(
        doc.getId(),
        doc.getType(),
        doc.getInteractionType(),
        doc.getInvolvedUserIds(),
        doc.getSubject(),
        doc.getContent(),
        doc.isReadStatus(),
        doc.getDeliveredAt(),
        doc.getReadAt(),
        doc.getDetails());
  }

  private String buildCredentialsBody(String fullName, String email, String temporaryPassword) {
    return """
                Hola %s,

                Se ha creado una cuenta para ti en el sistema inmobiliario.

                Credenciales temporales:
                Usuario: %s
                Contraseña temporal: %s

                Esta contraseña vence en 5 minutos y deberás cambiarla en tu primer ingreso.

                Saludos,
                Equipo de soporte
                """
        .formatted(fullName, email, temporaryPassword);
  }
}

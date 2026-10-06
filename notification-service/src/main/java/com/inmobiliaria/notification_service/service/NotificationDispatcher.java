package com.inmobiliaria.notification_service.service;

import com.inmobiliaria.notification_service.config.MailPropertiesConfig;
import com.inmobiliaria.notification_service.domain.NotificationDocument;
import com.inmobiliaria.notification_service.domain.NotificationStatus;
import com.inmobiliaria.notification_service.repository.NotificationRepository;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {
  private final JavaMailSender mailSender;
  private final MailPropertiesConfig mailProps;
  private final NotificationRepository notificationRepo;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  @Async
  public void send(NotificationDocument notif) {
    notif.setStatus(NotificationStatus.PENDING);
    notificationRepo.save(notif);
    try {
      if ("IN_APP".equalsIgnoreCase(notif.getChannel())) {
        // No necesita envío externo, solo marcar como entregado
        notif.setStatus(NotificationStatus.SENT);
        notif.setDeliveredAt(Instant.now());
        notif.setSentAt(Instant.now());
      } else if ("EMAIL".equalsIgnoreCase(notif.getChannel())) {
        sendByChannel(notif); // envía email
        notif.setStatus(NotificationStatus.SENT);
        notif.setSentAt(Instant.now());
      } else {
        throw new UnsupportedOperationException("Canal no soportado: " + notif.getChannel());
      }
    } catch (Exception e) {
      log.warn("Fallo en envío, reintentando...", e);
      scheduler.schedule(() -> retry(notif), 5, TimeUnit.SECONDS);
      return;
    }
    notificationRepo.save(notif);
  }

  private void retry(NotificationDocument notif) {
    try {
      sendByChannel(notif);
      notif.setStatus(NotificationStatus.SENT);
      notif.setSentAt(Instant.now());
    } catch (Exception e) {
      notif.setStatus(NotificationStatus.FAILED);
      notif.setErrorMessage(e.getMessage());
      log.error("Reintento fallido para notif {}", notif.getId(), e);
    }
    notificationRepo.save(notif);
  }

  private void sendByChannel(NotificationDocument notif) {
    if ("EMAIL".equalsIgnoreCase(notif.getChannel())) {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setFrom(mailProps.getFrom());
      msg.setTo(notif.getRecipientId()); // asumiendo que recipientId es email
      msg.setSubject(notif.getSubject());
      msg.setText(notif.getContent());
      mailSender.send(msg);
    } else {
      throw new UnsupportedOperationException("Canal no implementado: " + notif.getChannel());
    }
  }
}

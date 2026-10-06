package com.inmobiliaria.notification_service.repository;

import com.inmobiliaria.notification_service.domain.NotificationDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

  // Método genérico para obtener notificaciones por destinatario (todos los canales)
  Page<NotificationDocument> findByRecipientId(String recipientId, Pageable pageable);

  // Método específico para in-app ordenado por fecha
  Page<NotificationDocument> findByRecipientIdAndChannelOrderByCreatedAtDesc(
      String recipientId, String channel, Pageable pageable);

  // Método con query explícita para filtrar por estado de lectura (solo in-app)
  @Query("{ 'recipientId': ?0, 'channel': 'IN_APP', 'readStatus': ?1 }")
  Page<NotificationDocument> findByRecipientIdAndReadStatus(
      String recipientId, boolean readStatus, Pageable pageable);
}

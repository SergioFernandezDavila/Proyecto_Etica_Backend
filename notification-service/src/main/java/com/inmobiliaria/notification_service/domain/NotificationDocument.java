package com.inmobiliaria.notification_service.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@CompoundIndexes({
  @CompoundIndex(
      name = "idx_recipient_channel_created",
      def = "{'recipientId':1, 'channel':1, 'createdAt':-1}"),
  @CompoundIndex(name = "idx_recipient_readStatus", def = "{'recipientId':1, 'readStatus':1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDocument {
  @Id private String id;
  private String recipientId; // ID del propietario/usuario
  private String type; // ej: "VISIT_SCHEDULED", "CREDENTIALS"
  private String channel; // "EMAIL", "PUSH" (futuro)
  private String subject;
  private String content;
  private NotificationStatus status; // PENDING, SENT, FAILED
  private String errorMessage;
  private Integer retryCount;
  private LocalDateTime createdAt;
  private Instant sentAt;
  private InteractionType interactionType;
  private List<String> involvedUserIds; // IDs de usuarios afectados
  private Instant deliveredAt; // momento en que se entregó al cliente (in-app)
  private Instant readAt; // cuando el usuario marcó como leído
  @Builder.Default private boolean readStatus = false; // conveniencia
  private Map<String, Object> details; // datos extra (ej. resultado visita, precio anterior, etc.)
}

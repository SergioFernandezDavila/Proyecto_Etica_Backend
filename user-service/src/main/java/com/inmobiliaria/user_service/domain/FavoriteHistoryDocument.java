package com.inmobiliaria.user_service.domain;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "favorite_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteHistoryDocument {
  @Id private String id;

  @Indexed private String authUserId;

  @Indexed private String propertyId;

  private String action; // "ADDED" or "REMOVED"

  private Instant timestamp;
}

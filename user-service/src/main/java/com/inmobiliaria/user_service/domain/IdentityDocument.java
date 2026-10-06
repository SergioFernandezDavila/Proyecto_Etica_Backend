package com.inmobiliaria.user_service.domain;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "identity_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityDocument extends BaseDocument {

  @Id private String id;

  private String personId;
  private DocumentType documentType;
  private String fileUrl;
  private Instant uploadDate;
  private String uploadedById;
}

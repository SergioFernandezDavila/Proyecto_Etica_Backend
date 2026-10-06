package com.inmobiliaria.user_service.repository;

import com.inmobiliaria.user_service.domain.IdentityDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdentityDocumentRepository extends MongoRepository<IdentityDocument, String> {

  List<IdentityDocument> findByPersonId(String personId);
}

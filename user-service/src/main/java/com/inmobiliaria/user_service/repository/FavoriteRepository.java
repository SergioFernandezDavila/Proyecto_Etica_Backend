package com.inmobiliaria.user_service.repository;

import com.inmobiliaria.user_service.domain.FavoriteDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FavoriteRepository extends MongoRepository<FavoriteDocument, String> {
  List<FavoriteDocument> findByAuthUserId(String authUserId);

  Optional<FavoriteDocument> findByAuthUserIdAndPropertyId(String authUserId, String propertyId);

  void deleteByAuthUserIdAndPropertyId(String authUserId, String propertyId);

  boolean existsByAuthUserIdAndPropertyId(String authUserId, String propertyId);
}

package com.inmobiliaria.user_service.repository;

import com.inmobiliaria.user_service.domain.FavoriteHistoryDocument;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FavoriteHistoryRepository
    extends MongoRepository<FavoriteHistoryDocument, String> {
  List<FavoriteHistoryDocument> findByAuthUserIdOrderByTimestampDesc(
      String authUserId, Pageable pageable);

  List<FavoriteHistoryDocument> findByAuthUserIdAndPropertyIdOrderByTimestampDesc(
      String authUserId, String propertyId);
}

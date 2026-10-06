package com.inmobiliaria.identity_service.repository;

import com.inmobiliaria.identity_service.domain.UserDocument;
import com.inmobiliaria.identity_service.domain.UserStatus;
import com.inmobiliaria.identity_service.domain.UserType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<UserDocument, String> {

  Optional<UserDocument> findByEmailNormalized(String emailNormalized);

  Optional<UserDocument> findByRefreshToken(String refreshToken);

  boolean existsByEmailNormalized(String emailNormalized);

  @Query(
      "{ $and: [ "
          + "  { $or: [ { 'status': ?0 }, { $expr: { $eq: [?0, null] } } ] }, "
          + "  { $or: [ "
          + "    { 'fullName': { $regex: ?1, $options: 'i' } }, "
          + "    { 'email': { $regex: ?1, $options: 'i' } } "
          + "  ] } "
          + "] }")
  Page<UserDocument> findAllFiltered(UserStatus status, String query, Pageable pageable);

  List<UserDocument> findByUserType(UserType userType);
}

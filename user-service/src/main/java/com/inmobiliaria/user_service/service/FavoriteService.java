package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.client.NotificationClient;
import com.inmobiliaria.user_service.client.PropertyClient;
import com.inmobiliaria.user_service.domain.FavoriteDocument;
import com.inmobiliaria.user_service.domain.FavoriteHistoryDocument;
import com.inmobiliaria.user_service.dto.request.SendInAppNotificationRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.repository.FavoriteHistoryRepository;
import com.inmobiliaria.user_service.repository.FavoriteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final FavoriteHistoryRepository favoriteHistoryRepository;
  private final PersonService personService;
  private final PropertyClient propertyClient;
  private final NotificationClient notificationClient;

  public void addFavorite(String authUserId, String propertyId) {
    personService.validarClienteActivo(authUserId);
    // personService.updateLastActivityDate(authUserId);

    if (propertyId == null || propertyId.isBlank()) {
      throw new IllegalArgumentException("propertyId is required");
    }

    FavoriteDocument fav =
        favoriteRepository.findByAuthUserIdAndPropertyId(authUserId, propertyId).orElse(null);

    Instant now = Instant.now();

    if (fav != null) {
      if (fav.isActive()) {
        log.info("Favorite already active for user {} property {}", authUserId, propertyId);
        return;
      }
      fav.setActive(true);
      fav.setLastToggledAt(now);
      favoriteRepository.save(fav);
    } else {
      fav =
          FavoriteDocument.builder()
              .authUserId(authUserId)
              .propertyId(propertyId)
              .createdAt(now)
              .lastToggledAt(now)
              .active(true)
              .build();
      favoriteRepository.save(fav);
      sendFavoriteNotification(authUserId, propertyId, true);
    }

    favoriteHistoryRepository.save(
        FavoriteHistoryDocument.builder()
            .authUserId(authUserId)
            .propertyId(propertyId)
            .action("ADDED")
            .timestamp(now)
            .build());

    log.info("Favorite ADDED for user {} property {}", authUserId, propertyId);
  }

  public void removeFavorite(String authUserId, String propertyId) {
    personService.validarClienteActivo(authUserId);
    // personService.updateLastActivityDate(authUserId);

    FavoriteDocument fav =
        favoriteRepository.findByAuthUserIdAndPropertyId(authUserId, propertyId).orElse(null);

    Instant now = Instant.now();

    if (fav != null && fav.isActive()) {
      fav.setActive(false);
      fav.setLastToggledAt(now);
      favoriteRepository.save(fav);
      sendFavoriteNotification(authUserId, propertyId, false);

      favoriteHistoryRepository.save(
          FavoriteHistoryDocument.builder()
              .authUserId(authUserId)
              .propertyId(propertyId)
              .action("REMOVED")
              .timestamp(now)
              .build());

      log.info("Favorite REMOVED for user {} property {}", authUserId, propertyId);
    } else {
      log.info(
          "Favorite not active, nothing to remove for user {} property {}", authUserId, propertyId);
    }
  }

  public List<String> getFavoriteIdsByClient(String authUserId) {
    return favoriteRepository.findByAuthUserId(authUserId).stream()
        .filter(FavoriteDocument::isActive)
        .map(FavoriteDocument::getPropertyId)
        .toList();
  }

  public List<Map<String, Object>> getFavoriteHistory(String authUserId, int limit) {
    return favoriteHistoryRepository
        .findByAuthUserIdOrderByTimestampDesc(authUserId, PageRequest.of(0, Math.min(limit, 100)))
        .stream()
        .map(this::toHistoryMap)
        .toList();
  }

  public List<Map<String, Object>> getPropertyFavoriteHistory(
      String authUserId, String propertyId) {
    return favoriteHistoryRepository
        .findByAuthUserIdAndPropertyIdOrderByTimestampDesc(authUserId, propertyId)
        .stream()
        .map(this::toHistoryMap)
        .toList();
  }

  private Map<String, Object> toHistoryMap(FavoriteHistoryDocument doc) {
    return Map.of(
        "propertyId", doc.getPropertyId(),
        "action", doc.getAction(),
        "timestamp", doc.getTimestamp().toString());
  }

  @Async
  protected void sendFavoriteNotification(String authUserId, String propertyId, boolean added) {
    try {
      // Obtener datos de la propiedad
      PropertyClient.PropertyResponse property = propertyClient.getProperty(propertyId);
      if (property.ownerId() == null || property.ownerId().isBlank()) {
        log.warn("Property {} has no owner, notification skipped", propertyId);
        return;
      }

      // Obtener datos del cliente
      PersonResponse client = personService.findByAuthUserId(authUserId);
      String clientName = client.fullName() != null ? client.fullName() : authUserId;

      String actionText = added ? "añadido" : "eliminado";
      String subject = "Actualización en favoritos";
      String content =
          String.format(
              "El cliente %s ha %s tu propiedad '%s' de sus favoritos.",
              clientName, actionText, property.title());

      Map<String, Object> details =
          Map.of(
              "clientId", authUserId,
              "clientName", clientName,
              "propertyId", propertyId,
              "propertyTitle", property.title(),
              "action", added ? "ADDED" : "REMOVED");

      SendInAppNotificationRequest request =
          new SendInAppNotificationRequest(
              property.ownerId(),
              added ? "FAVORITE_ADDED" : "FAVORITE_REMOVED",
              "INTERES",
              List.of(authUserId),
              subject,
              content,
              details);

      notificationClient.sendInAppNotification(request);
      log.info(
          "Notification sent to owner {} for property {} by client {}",
          property.ownerId(),
          propertyId,
          authUserId);
    } catch (Exception e) {
      log.error(
          "Failed to send notification for favorite {} by user {}: {}",
          added ? "add" : "remove",
          authUserId,
          e.getMessage());
    }
  }
}

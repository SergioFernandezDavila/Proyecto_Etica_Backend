package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.dto.response.ApiResponse;
import com.inmobiliaria.user_service.dto.response.ResponseFactory;
import com.inmobiliaria.user_service.service.FavoriteService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/favoritos")
@RequiredArgsConstructor
public class FavoriteController {
  private final FavoriteService favoriteService;
  private final ResponseFactory responseFactory;

  @PostMapping
  public ResponseEntity<ApiResponse<Void>> add(
      @RequestBody Map<String, String> body,
      @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    favoriteService.addFavorite(userId, body.get("propertyId"));
    return ResponseEntity.status(201)
        .body(responseFactory.created("Favorite added successfully", null));
  }

  @DeleteMapping("/{propertyId}")
  public ResponseEntity<ApiResponse<Void>> remove(
      @PathVariable String propertyId,
      @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    favoriteService.removeFavorite(userId, propertyId);
    return ResponseEntity.status(204)
        .body(responseFactory.deleted("Favorite removed successfully"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<String>>> list(
      @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    List<String> data = favoriteService.getFavoriteIdsByClient(userId);
    return ResponseEntity.ok(responseFactory.success("Favorites retrieved successfully", data));
  }

  @GetMapping("/history")
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(
      @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
      @RequestParam(defaultValue = "20") int limit) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    List<Map<String, Object>> data = favoriteService.getFavoriteHistory(userId, limit);
    return ResponseEntity.ok(
        responseFactory.success("Favorite history retrieved successfully", data));
  }

  @GetMapping("/history/{propertyId}")
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> propertyHistory(
      @PathVariable String propertyId,
      @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Missing X-Auth-User-Id header");
    }
    List<Map<String, Object>> data = favoriteService.getPropertyFavoriteHistory(userId, propertyId);
    return ResponseEntity.ok(
        responseFactory.success("Property favorite history retrieved successfully", data));
  }
}

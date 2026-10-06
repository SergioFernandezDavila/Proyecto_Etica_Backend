package com.inmobiliaria.identity_service.controller;

import com.inmobiliaria.identity_service.dto.request.ChangePasswordRequest;
import com.inmobiliaria.identity_service.dto.request.LoginRequest;
import com.inmobiliaria.identity_service.dto.request.RefreshTokenRequest;
import com.inmobiliaria.identity_service.dto.request.ResendTempPasswordRequest;
import com.inmobiliaria.identity_service.dto.response.ApiResponse;
import com.inmobiliaria.identity_service.dto.response.AuthResponse;
import com.inmobiliaria.identity_service.dto.response.ResponseFactory;
import com.inmobiliaria.identity_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final ResponseFactory responseFactory;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(responseFactory.success("Authentication successful", response));
  }

  @PostMapping("/change-password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(request);
    return ResponseEntity.ok(responseFactory.success("Password changed successfully", null));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @Valid @RequestBody RefreshTokenRequest request) {
    AuthResponse response = authService.refresh(request.refreshToken());
    return ResponseEntity.ok(responseFactory.success("Token refreshed successfully", response));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.ok(responseFactory.success("Logged out successfully", null));
  }

  @PostMapping("/resend-temp-password")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> resendTemporaryPassword(
      @Valid @RequestBody ResendTempPasswordRequest request) {
    authService.resendTemporaryPassword(request);
    return ResponseEntity.ok(
        responseFactory.success("Temporary password resent successfully", null));
  }
}

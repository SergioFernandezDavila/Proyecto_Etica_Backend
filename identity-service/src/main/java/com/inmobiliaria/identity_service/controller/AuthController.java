package com.inmobiliaria.identity_service.controller;

import com.inmobiliaria.identity_service.dto.request.ChangePasswordRequest;
import com.inmobiliaria.identity_service.dto.request.LoginRequest;
import com.inmobiliaria.identity_service.dto.request.RefreshTokenRequest;
import com.inmobiliaria.identity_service.dto.request.ResendTempPasswordRequest;
import com.inmobiliaria.identity_service.dto.request.VerifyMfaRequest;
import com.inmobiliaria.identity_service.dto.response.ApiResponse;
import com.inmobiliaria.identity_service.dto.response.AuthResponse;
import com.inmobiliaria.identity_service.dto.response.ResponseFactory;
import com.inmobiliaria.identity_service.service.AuthService;
import com.inmobiliaria.identity_service.service.ReCaptchaService;
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
  private final ReCaptchaService reCaptchaService;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    reCaptchaService.verify(request.recaptchaToken());
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(responseFactory.success("Authentication successful", response));
  }

  @PostMapping("/verify-mfa")
  public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(
      @Valid @RequestBody VerifyMfaRequest request) {
    AuthResponse response = authService.verifyMfa(request);
    return ResponseEntity.ok(
        responseFactory.success("Two-factor authentication successful", response));
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

  @PostMapping("/2fa/verify-login")
  public ResponseEntity<ApiResponse<AuthResponse>> verifyTwoFactorLogin(
      @Valid @RequestBody
          com.inmobiliaria.identity_service.dto.request.TwoFactorLoginRequest request) {
    AuthResponse response = authService.verifyTwoFactorLogin(request);
    return ResponseEntity.ok(responseFactory.success("2FA verification successful", response));
  }

  @PostMapping("/2fa/setup")
  public ResponseEntity<
          ApiResponse<com.inmobiliaria.identity_service.dto.response.TwoFactorSetupResponse>>
      setupTwoFactor(
          @RequestHeader(value = "X-Auth-User-Id", required = false) String headerUserId) {
    String userId = resolveUserId(headerUserId);
    com.inmobiliaria.identity_service.dto.response.TwoFactorSetupResponse response =
        authService.setupTwoFactor(userId);
    return ResponseEntity.ok(responseFactory.success("2FA setup initiated", response));
  }

  @PostMapping("/2fa/confirm")
  public ResponseEntity<ApiResponse<Void>> confirmTwoFactor(
      @RequestHeader(value = "X-Auth-User-Id", required = false) String headerUserId,
      @Valid @RequestBody
          com.inmobiliaria.identity_service.dto.request.TwoFactorVerifyRequest request) {
    String userId = resolveUserId(headerUserId);
    authService.confirmTwoFactor(userId, request.code());
    return ResponseEntity.ok(responseFactory.success("2FA enabled successfully", null));
  }

  @PostMapping("/2fa/disable")
  public ResponseEntity<ApiResponse<Void>> disableTwoFactor(
      @RequestHeader(value = "X-Auth-User-Id", required = false) String headerUserId,
      @Valid @RequestBody
          com.inmobiliaria.identity_service.dto.request.TwoFactorVerifyRequest request) {
    String userId = resolveUserId(headerUserId);
    authService.disableTwoFactor(userId, request.code());
    return ResponseEntity.ok(responseFactory.success("2FA disabled successfully", null));
  }

  private String resolveUserId(String headerUserId) {
    if (headerUserId != null && !headerUserId.isBlank()) {
      return headerUserId;
    }
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
      return auth.getName();
    }
    throw new com.inmobiliaria.identity_service.exception.UnauthorizedException(
        "User is not authenticated");
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

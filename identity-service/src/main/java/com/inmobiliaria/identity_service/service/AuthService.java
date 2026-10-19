package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.client.NotificationClient;
import com.inmobiliaria.identity_service.client.UserServiceClient;
import com.inmobiliaria.identity_service.domain.UserDocument;
import com.inmobiliaria.identity_service.domain.UserStatus;
import com.inmobiliaria.identity_service.domain.UserType;
import com.inmobiliaria.identity_service.dto.request.ChangePasswordRequest;
import com.inmobiliaria.identity_service.dto.request.LoginRequest;
import com.inmobiliaria.identity_service.dto.request.ResendTempPasswordRequest;
import com.inmobiliaria.identity_service.dto.request.TwoFactorLoginRequest;
import com.inmobiliaria.identity_service.dto.response.AuthResponse;
import com.inmobiliaria.identity_service.dto.response.TwoFactorSetupResponse;
import com.inmobiliaria.identity_service.exception.EmailSendException;
import com.inmobiliaria.identity_service.exception.TemporaryPasswordExpiredException;
import com.inmobiliaria.identity_service.exception.UnauthorizedException;
import com.inmobiliaria.identity_service.security.Auditable;
import com.inmobiliaria.identity_service.security.JwtService;
import com.inmobiliaria.identity_service.security.UserPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserService userService;
  private final BCryptPasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RoleClientService roleClientService;
  private final NotificationClient notificationClient;
  private final UserServiceClient userServiceClient;
  private final TotpService totpService;

  @Auditable(action = "USER_LOGIN", description = "User logged in")
  public AuthResponse login(LoginRequest request) {
    log.debug("Login attempt for email: {}", request.email());
    UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());
    log.debug("User found: ID={}, Status={}", user.getId(), user.getStatus());

    if (user.getStatus() != UserStatus.ACTIVE) {
      log.warn("Login failed: user {} is not active (Status: {})", user.getId(), user.getStatus());
      throw new UnauthorizedException("User is not active");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      log.warn("Login failed: invalid password for user {}", user.getId());
      throw new UnauthorizedException("Invalid credentials");
    }

    if (Boolean.TRUE.equals(user.getTemporaryPassword())
        && user.getTemporaryPasswordExpiresAt() != null
        && Instant.now().isAfter(user.getTemporaryPasswordExpiresAt())) {
      throw new TemporaryPasswordExpiredException("Temporary password has expired");
    }

    // If 2FA is enabled, return a pre-auth token requiring second factor
    if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
      log.info("2FA required for user: {}", user.getId());
      String preAuthToken = jwtService.generatePreAuthToken(user.getId());
      return AuthResponse.requires2fa(preAuthToken);
    }

    return issueTokens(user);
  }

  @Auditable(action = "USER_LOGIN_2FA", description = "User logged in with 2FA")
  public AuthResponse verifyTwoFactorLogin(TwoFactorLoginRequest request) {
    String userId = jwtService.validatePreAuthTokenAndGetUserId(request.preAuthToken());
    UserDocument user = userService.findDocumentById(userId);

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new UnauthorizedException("User is not active");
    }

    if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTwoFactorSecret() == null) {
      throw new UnauthorizedException("2FA is not enabled for this user");
    }

    boolean valid = totpService.verifyCode(user.getTwoFactorSecret(), request.code());
    if (!valid) {
      log.warn("Invalid 2FA code provided for user: {}", userId);
      throw new UnauthorizedException("Invalid 2FA verification code");
    }

    log.info("2FA verification succeeded for user: {}", userId);
    return issueTokens(user);
  }

  public TwoFactorSetupResponse setupTwoFactor(String userId) {
    UserDocument user = userService.findDocumentById(userId);
    String secret = totpService.generateSecret();
    user.setTwoFactorTempSecret(secret);
    userService.save(user);

    String otpAuthUrl = totpService.generateOtpAuthUrl("Inmobiliaria", user.getEmail(), secret);
    String qrCodeDataUri = totpService.generateQrCodeDataUri(otpAuthUrl);

    return new TwoFactorSetupResponse(secret, qrCodeDataUri, otpAuthUrl);
  }

  public void confirmTwoFactor(String userId, String code) {
    UserDocument user = userService.findDocumentById(userId);
    if (user.getTwoFactorTempSecret() == null) {
      throw new UnauthorizedException("No pending 2FA setup found. Please start setup again.");
    }

    boolean valid = totpService.verifyCode(user.getTwoFactorTempSecret(), code);
    if (!valid) {
      throw new UnauthorizedException("Invalid 2FA code. Please check your authenticator app.");
    }

    user.setTwoFactorSecret(user.getTwoFactorTempSecret());
    user.setTwoFactorTempSecret(null);
    user.setTwoFactorEnabled(true);
    userService.save(user);
    log.info("2FA successfully enabled for user: {}", userId);
  }

  public void disableTwoFactor(String userId, String code) {
    UserDocument user = userService.findDocumentById(userId);
    if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTwoFactorSecret() == null) {
      throw new UnauthorizedException("2FA is not enabled for this user");
    }

    boolean valid = totpService.verifyCode(user.getTwoFactorSecret(), code);
    if (!valid) {
      throw new UnauthorizedException("Invalid 2FA code");
    }

    user.setTwoFactorEnabled(false);
    user.setTwoFactorSecret(null);
    user.setTwoFactorTempSecret(null);
    userService.save(user);
    log.info("2FA successfully disabled for user: {}", userId);
  }

  private AuthResponse issueTokens(UserDocument user) {
    if (user.getUserType() == UserType.INTERESTED_CLIENT) {
      try {
        userServiceClient.updateLastActivity(user.getId());
        log.info("Updated lastActivityDate for client: {}", user.getId());
      } catch (Exception e) {
        log.warn(
            "Could not update lastActivityDate for client {}: {}", user.getId(), e.getMessage());
      }
    }

    String refreshToken = UUID.randomUUID().toString();
    user.setRefreshToken(refreshToken);
    user.setRefreshTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    user.setLastLoginAt(Instant.now());
    userService.save(user);

    List<String> roleCodes = roleClientService.resolveRoleCodes(user.getPrimaryRoleIds());

    UserPrincipal principal =
        new UserPrincipal(
            user.getId(),
            user.getEmailNormalized(),
            roleCodes,
            user.getUserType().name(),
            user.getStatus().name());

    return new AuthResponse(
        user.getId(),
        jwtService.generateAccessToken(principal),
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        Boolean.TRUE.equals(user.getMustChangePassword()));
  }

  public AuthResponse refresh(String refreshToken) {
    UserDocument user = userService.findByRefreshToken(refreshToken);

    if (user == null || user.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
      throw new UnauthorizedException("Invalid or expired refresh token");
    }

    String newRefreshToken = UUID.randomUUID().toString();
    user.setRefreshToken(newRefreshToken);
    user.setRefreshTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    userService.save(user);

    List<String> roleCodes = roleClientService.resolveRoleCodes(user.getPrimaryRoleIds());

    UserPrincipal principal =
        new UserPrincipal(
            user.getId(),
            user.getEmailNormalized(),
            roleCodes,
            user.getUserType().name(),
            user.getStatus().name());

    return new AuthResponse(
        user.getId(),
        jwtService.generateAccessToken(principal),
        newRefreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        Boolean.TRUE.equals(user.getMustChangePassword()));
  }

  @Auditable(action = "USER_LOGOUT", description = "User logged out")
  public void logout(String refreshToken) {
    UserDocument user = userService.findByRefreshToken(refreshToken);
    if (user != null) {
      user.setRefreshToken(null);
      user.setRefreshTokenExpiresAt(null);
      userService.save(user);
    }
  }

  @Auditable(action = "PASSWORD_CHANGE", description = "User changed password")
  public void changePassword(ChangePasswordRequest request) {
    UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new UnauthorizedException("Current password is invalid");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setTemporaryPassword(false);
    user.setTemporaryPasswordExpiresAt(null);
    user.setMustChangePassword(false);
    user.setPasswordChangedAt(Instant.now());

    userService.save(user);
  }

  public void resendTemporaryPassword(ResendTempPasswordRequest request) {
    UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());

    if (!Boolean.TRUE.equals(user.getTemporaryPassword())
        && !Boolean.TRUE.equals(user.getMustChangePassword())) {
      throw new UnauthorizedException("User does not require temporary password reset");
    }

    String newTemporaryPassword = userService.generateTemporaryPassword();

    user.setPasswordHash(passwordEncoder.encode(newTemporaryPassword));
    user.setTemporaryPassword(true);
    user.setTemporaryPasswordExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
    user.setMustChangePassword(true);
    user.setPasswordChangedAt(null);
    userService.save(user);

    try {
      notificationClient.sendCredentialsEmail(
          NotificationClient.CredentialsRequest.builder()
              .to(user.getEmailNormalized())
              .fullName(user.getFullName())
              .temporaryPassword(newTemporaryPassword)
              .build());
    } catch (Exception e) {
      log.error("Failed to resend credentials email to: {}", user.getEmailNormalized(), e);
      throw new EmailSendException("Failed to send email", e);
    }
  }
}

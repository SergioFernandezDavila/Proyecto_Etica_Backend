package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.client.NotificationClient;
import com.inmobiliaria.identity_service.client.UserServiceClient;
import com.inmobiliaria.identity_service.domain.AuditLog;
import com.inmobiliaria.identity_service.domain.UserDocument;
import com.inmobiliaria.identity_service.domain.UserStatus;
import com.inmobiliaria.identity_service.domain.UserType;
import com.inmobiliaria.identity_service.dto.request.ChangePasswordRequest;
import com.inmobiliaria.identity_service.dto.request.LoginRequest;
import com.inmobiliaria.identity_service.dto.request.ResendTempPasswordRequest;
import com.inmobiliaria.identity_service.dto.request.VerifyMfaRequest;
import com.inmobiliaria.identity_service.dto.response.AuthResponse;
import com.inmobiliaria.identity_service.exception.EmailSendException;
import com.inmobiliaria.identity_service.exception.TemporaryPasswordExpiredException;
import com.inmobiliaria.identity_service.exception.UnauthorizedException;
import com.inmobiliaria.identity_service.security.Auditable;
import com.inmobiliaria.identity_service.security.JwtService;
import com.inmobiliaria.identity_service.security.PasswordPolicyValidator;
import com.inmobiliaria.identity_service.security.UserPrincipal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final AuditService auditService;

  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    log.debug("Login attempt for email: {}", normalizedEmail);

    UserDocument user = userService.findByEmailNormalized(normalizedEmail);
    log.debug("User found: ID={}, Status={}", user.getId(), user.getStatus());

    // 1. Verificar bloqueo temporal por intentos fallidos
    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
      long minutesRemaining =
          Duration.between(Instant.now(), user.getLockedUntil()).toMinutes() + 1;
      auditService.record(
          AuditLog.builder()
              .action("USER_LOGIN_FAILED")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details(
                  "Intento de acceso en cuenta bloqueada temporalmente ("
                      + minutesRemaining
                      + " min restantes)")
              .status("FAILURE")
              .build());
      throw new UnauthorizedException(
          "La cuenta se encuentra temporalmente bloqueada por varios intentos fallidos. Intente de nuevo en "
              + minutesRemaining
              + " minutos.");
    }

    // 2. Verificar si está bloqueado por el administrador
    if (user.getStatus() == UserStatus.LOCKED) {
      auditService.record(
          AuditLog.builder()
              .action("USER_LOGIN_FAILED")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details("Intento de acceso a cuenta bloqueada por administrador")
              .status("FAILURE")
              .build());
      throw new UnauthorizedException("La cuenta se encuentra bloqueada por un administrador.");
    }

    // 3. Verificar estado activo
    if (user.getStatus() != UserStatus.ACTIVE) {
      auditService.record(
          AuditLog.builder()
              .action("USER_LOGIN_FAILED")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details("Usuario inactivo (Status: " + user.getStatus() + ")")
              .status("FAILURE")
              .build());
      throw new UnauthorizedException("User is not active");
    }

    // 4. Validar contraseña y conteo de fallos
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      int attempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() + 1 : 1;
      user.setFailedLoginAttempts(attempts);

      if (attempts >= 5) {
        user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        userService.save(user);

        auditService.record(
            AuditLog.builder()
                .action("USER_LOCKOUT")
                .userId(user.getId())
                .userEmail(user.getEmailNormalized())
                .details(
                    "Cuenta bloqueada temporalmente por 15 minutos debido a 5 intentos fallidos")
                .status("FAILURE")
                .build());

        auditService.record(
            AuditLog.builder()
                .action("USER_LOGIN_FAILED")
                .userId(user.getId())
                .userEmail(user.getEmailNormalized())
                .details(
                    "Credenciales inválidas (Intento "
                        + attempts
                        + "/5). Cuenta bloqueada temporalmente.")
                .status("FAILURE")
                .build());

        throw new UnauthorizedException(
            "Demasiados intentos fallidos. La cuenta ha sido bloqueada temporalmente por 15 minutos.");
      } else {
        userService.save(user);
        auditService.record(
            AuditLog.builder()
                .action("USER_LOGIN_FAILED")
                .userId(user.getId())
                .userEmail(user.getEmailNormalized())
                .details("Credenciales inválidas (Intento " + attempts + "/5)")
                .status("FAILURE")
                .build());
        throw new UnauthorizedException("Invalid credentials");
      }
    }

    // 5. Verificar vigencia de contraseña temporal
    if (Boolean.TRUE.equals(user.getTemporaryPassword())
        && user.getTemporaryPasswordExpiresAt() != null
        && Instant.now().isAfter(user.getTemporaryPasswordExpiresAt())) {
      auditService.record(
          AuditLog.builder()
              .action("USER_LOGIN_FAILED")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details("Contraseña temporal expirada")
              .status("FAILURE")
              .build());
      throw new TemporaryPasswordExpiredException("Temporary password has expired");
    }

    // 6. Verificar MFA
    if (Boolean.TRUE.equals(user.getMfaEnabled())) {
      String mfaOtp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
      user.setMfaCode(mfaOtp);
      user.setMfaCodeExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
      userService.save(user);

      try {
        notificationClient.sendMfaEmail(
            NotificationClient.MfaEmailRequest.builder()
                .to(user.getEmailNormalized())
                .fullName(user.getFullName())
                .code(mfaOtp)
                .build());
      } catch (Exception e) {
        log.error("Failed to send MFA email to {}: {}", user.getEmailNormalized(), e.getMessage());
      }

      auditService.record(
          AuditLog.builder()
              .action("MFA_CHALLENGE")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details("Desafío MFA generado y enviado por correo electrónico")
              .status("SUCCESS")
              .build());

      return new AuthResponse(
          user.getId(),
          null,
          null,
          "Bearer",
          0,
          Boolean.TRUE.equals(user.getMustChangePassword()),
          true);
    }

    // 7. Flujo normal exitoso sin MFA
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);

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

    auditService.record(
        AuditLog.builder()
            .action("USER_LOGIN_SUCCESS")
            .userId(user.getId())
            .userEmail(user.getEmailNormalized())
            .details("Inicio de sesión exitoso")
            .status("SUCCESS")
            .build());

    return new AuthResponse(
        user.getId(),
        jwtService.generateAccessToken(principal),
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        Boolean.TRUE.equals(user.getMustChangePassword()),
        false);
  }

  public AuthResponse verifyMfa(VerifyMfaRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    UserDocument user = userService.findByEmailNormalized(normalizedEmail);

    if (user.getMfaCode() == null
        || user.getMfaCodeExpiresAt() == null
        || Instant.now().isAfter(user.getMfaCodeExpiresAt())) {
      auditService.record(
          AuditLog.builder()
              .action("MFA_FAILED")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details("Código MFA expirado o no solicitado")
              .status("FAILURE")
              .build());
      throw new UnauthorizedException("El código de verificación ha expirado o no es válido");
    }

    if (!user.getMfaCode().equals(request.mfaCode().trim())) {
      auditService.record(
          AuditLog.builder()
              .action("MFA_FAILED")
              .userId(user.getId())
              .userEmail(user.getEmailNormalized())
              .details("Código MFA incorrecto ingresado")
              .status("FAILURE")
              .build());
      throw new UnauthorizedException("Código de verificación incorrecto");
    }

    // Código válido: limpiar MFA y reiniciar intentos fallidos
    user.setMfaCode(null);
    user.setMfaCodeExpiresAt(null);
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);

    if (user.getUserType() == UserType.INTERESTED_CLIENT) {
      try {
        userServiceClient.updateLastActivity(user.getId());
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

    auditService.record(
        AuditLog.builder()
            .action("USER_LOGIN_SUCCESS")
            .userId(user.getId())
            .userEmail(user.getEmailNormalized())
            .details("Autenticación en dos pasos (MFA) exitosa")
            .status("SUCCESS")
            .build());

    return new AuthResponse(
        user.getId(),
        jwtService.generateAccessToken(principal),
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        Boolean.TRUE.equals(user.getMustChangePassword()),
        false);
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
        Boolean.TRUE.equals(user.getMustChangePassword()),
        false);
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

  public void changePassword(ChangePasswordRequest request) {
    UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new UnauthorizedException("Current password is invalid");
    }

    // Validar políticas de contraseñas (12 chars, complejidad, común e histórico de las últimas 5)
    passwordPolicyValidator.validate(request.newPassword(), user);

    // Guardar hash actual en el histórico de contraseñas
    List<String> history = user.getPreviousPasswordHashes();
    if (history == null) {
      history = new ArrayList<>();
    } else {
      history = new ArrayList<>(history);
    }
    if (user.getPasswordHash() != null) {
      history.add(0, user.getPasswordHash());
      if (history.size() > 5) {
        history = history.subList(0, 5);
      }
    }
    user.setPreviousPasswordHashes(history);

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setTemporaryPassword(false);
    user.setTemporaryPasswordExpiresAt(null);
    user.setMustChangePassword(false);
    user.setPasswordChangedAt(Instant.now());
    user.setPasswordExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));

    userService.save(user);

    auditService.record(
        AuditLog.builder()
            .action("PASSWORD_CHANGE")
            .userId(user.getId())
            .userEmail(user.getEmailNormalized())
            .details("Cambio de contraseña exitoso conforme a políticas de seguridad")
            .status("SUCCESS")
            .build());
  }

  public void resendTemporaryPassword(ResendTempPasswordRequest request) {
    UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());

    if (!Boolean.TRUE.equals(user.getTemporaryPassword())
        && !Boolean.TRUE.equals(user.getMustChangePassword())) {
      throw new UnauthorizedException("User does not require temporary password reset");
    }

    String newTemporaryPassword = passwordPolicyValidator.generateCompliantPassword();

    // Guardar hash anterior en histórico si existía
    List<String> history = user.getPreviousPasswordHashes();
    if (history == null) {
      history = new ArrayList<>();
    } else {
      history = new ArrayList<>(history);
    }
    if (user.getPasswordHash() != null) {
      history.add(0, user.getPasswordHash());
      if (history.size() > 5) {
        history = history.subList(0, 5);
      }
    }
    user.setPreviousPasswordHashes(history);

    user.setPasswordHash(passwordEncoder.encode(newTemporaryPassword));
    user.setTemporaryPassword(true);
    user.setTemporaryPasswordExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
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

    auditService.record(
        AuditLog.builder()
            .action("PASSWORD_RESET")
            .userId(user.getId())
            .userEmail(user.getEmailNormalized())
            .details("Reenvío de contraseña temporal")
            .status("SUCCESS")
            .build());
  }
}

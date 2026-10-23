package com.inmobiliaria.identity_service.security;

import com.inmobiliaria.identity_service.domain.UserDocument;
import com.inmobiliaria.identity_service.exception.ValidationException;
import java.security.SecureRandom;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

  private final BCryptPasswordEncoder passwordEncoder;

  private static final int MIN_LENGTH = 12;
  private static final Set<String> COMMON_PASSWORDS =
      Set.of(
          "password12345",
          "admin12345678",
          "123456789012",
          "qwertyuiopas",
          "inmobiliaria12",
          "welcome123456",
          "contrasena123",
          "password123!",
          "admin123456!",
          "inmobiliaria2026!");

  public void validate(String newPassword, UserDocument user) {
    if (newPassword == null || newPassword.length() < MIN_LENGTH) {
      throw new ValidationException(
          "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
    }

    // Reglas de complejidad
    boolean hasUpper = newPassword.chars().anyMatch(Character::isUpperCase);
    boolean hasLower = newPassword.chars().anyMatch(Character::isLowerCase);
    boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
    boolean hasSpecial =
        newPassword.chars().anyMatch(ch -> "!@#$%^&*()-_=+[]{}|;:,.<>?".indexOf(ch) >= 0);

    if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
      throw new ValidationException(
          "La contraseña debe contener al menos una mayúscula, una minúscula, un número y un carácter especial.");
    }

    if (COMMON_PASSWORDS.contains(newPassword.toLowerCase())) {
      throw new ValidationException("La contraseña es demasiado común o trivial.");
    }

    // Validación de histórico de las últimas 5 contraseñas
    if (user != null && user.getPreviousPasswordHashes() != null) {
      for (String oldHash : user.getPreviousPasswordHashes()) {
        if (passwordEncoder.matches(newPassword, oldHash)) {
          throw new ValidationException(
              "No se puede reutilizar ninguna de las últimas 5 contraseñas.");
        }
      }
    }
  }

  public String generateCompliantPassword() {
    String uppers = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    String lowers = "abcdefghijkmnpqrstuvwxyz";
    String digits = "23456789";
    String specials = "!@#$%&*-_+=";
    String all = uppers + lowers + digits + specials;

    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder();
    sb.append(uppers.charAt(random.nextInt(uppers.length())));
    sb.append(lowers.charAt(random.nextInt(lowers.length())));
    sb.append(digits.charAt(random.nextInt(digits.length())));
    sb.append(specials.charAt(random.nextInt(specials.length())));

    for (int i = 4; i < 14; i++) {
      sb.append(all.charAt(random.nextInt(all.length())));
    }

    // Shuffle
    char[] chars = sb.toString().toCharArray();
    for (int i = chars.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      char temp = chars[i];
      chars[i] = chars[j];
      chars[j] = temp;
    }
    return new String(chars);
  }
}

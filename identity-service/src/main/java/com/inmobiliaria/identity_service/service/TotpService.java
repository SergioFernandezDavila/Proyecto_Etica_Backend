package com.inmobiliaria.identity_service.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TotpService {

  private static final String HMAC_ALGO = "HmacSHA1";
  private static final int TIME_STEP_SECONDS = 30;
  private static final int CODE_DIGITS = 6;
  private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Generates a 20-byte cryptographically secure random Base32 secret key. */
  public String generateSecret() {
    byte[] buffer = new byte[20];
    SECURE_RANDOM.nextBytes(buffer);
    return encodeBase32(buffer);
  }

  /** Generates the standard otpauth URI recognized by Google Authenticator. */
  public String generateOtpAuthUrl(String issuer, String accountName, String secret) {
    String cleanIssuer = issuer.trim();
    String cleanAccount = accountName.trim();
    return String.format(
        "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
        cleanIssuer, cleanAccount, secret, cleanIssuer, CODE_DIGITS, TIME_STEP_SECONDS);
  }

  /** Generates a PNG QR Code image as a Base64 data URI string. */
  public String generateQrCodeDataUri(String otpAuthUrl) {
    try {
      QRCodeWriter qrCodeWriter = new QRCodeWriter();
      BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 240, 240);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
      byte[] pngBytes = outputStream.toByteArray();
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    } catch (Exception ex) {
      log.error("Failed to generate QR code image for TOTP URL", ex);
      throw new IllegalStateException("Failed to generate QR code", ex);
    }
  }

  /**
   * Validates a 6-digit TOTP code against the secret with a +/- 1 time-step window for clock drift.
   */
  public boolean verifyCode(String secret, String code) {
    if (secret == null || code == null || code.trim().length() != CODE_DIGITS) {
      return false;
    }

    try {
      byte[] keyBytes = decodeBase32(secret.trim().toUpperCase());
      long currentWindow = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;

      // Check current window and +/- 1 window for clock drift
      for (int i = -1; i <= 1; i++) {
        long window = currentWindow + i;
        if (generateTotpCode(keyBytes, window).equals(code.trim())) {
          return true;
        }
      }
      return false;
    } catch (Exception ex) {
      log.warn("Error verifying TOTP code: {}", ex.getMessage());
      return false;
    }
  }

  private String generateTotpCode(byte[] key, long timeWindow) throws Exception {
    byte[] data = ByteBuffer.allocate(8).putLong(timeWindow).array();
    Mac mac = Mac.getInstance(HMAC_ALGO);
    mac.init(new SecretKeySpec(key, HMAC_ALGO));
    byte[] hash = mac.doFinal(data);

    int offset = hash[hash.length - 1] & 0x0F;
    int binary =
        ((hash[offset] & 0x7F) << 24)
            | ((hash[offset + 1] & 0xFF) << 16)
            | ((hash[offset + 2] & 0xFF) << 8)
            | (hash[offset + 3] & 0xFF);

    int otp = binary % 1_000_000;
    return String.format("%06d", otp);
  }

  private String encodeBase32(byte[] data) {
    StringBuilder result = new StringBuilder();
    int buffer = 0;
    int next = 0;
    int bitsLeft = 0;

    while (next < data.length) {
      buffer = (buffer << 8) | (data[next++] & 0xFF);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        bitsLeft -= 5;
        result.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
      }
    }

    if (bitsLeft > 0) {
      buffer <<= (5 - bitsLeft);
      result.append(BASE32_CHARS.charAt(buffer & 0x1F));
    }

    return result.toString();
  }

  private byte[] decodeBase32(String base32) {
    String cleanInput = base32.replaceAll("[^A-Z2-7]", "");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bitsLeft = 0;

    for (int i = 0; i < cleanInput.length(); i++) {
      char c = cleanInput.charAt(i);
      int val = BASE32_CHARS.indexOf(c);
      if (val < 0) {
        continue;
      }
      buffer = (buffer << 5) | val;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        bitsLeft -= 8;
        out.write((buffer >> bitsLeft) & 0xFF);
      }
    }

    return out.toByteArray();
  }
}

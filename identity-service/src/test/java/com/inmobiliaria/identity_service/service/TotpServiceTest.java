package com.inmobiliaria.identity_service.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

  private TotpService totpService;

  @BeforeEach
  void setUp() {
    totpService = new TotpService();
  }

  @Test
  void shouldGenerateValidSecret() {
    String secret = totpService.generateSecret();
    assertNotNull(secret);
    assertTrue(secret.length() >= 16);
    assertTrue(secret.matches("^[A-Z2-7]+$"));
  }

  @Test
  void shouldGenerateOtpAuthUrl() {
    String url =
        totpService.generateOtpAuthUrl("Inmobiliaria", "admin@admin.com", "JBSWY3DPEHPK3PXP");
    assertNotNull(url);
    assertTrue(url.startsWith("otpauth://totp/Inmobiliaria:admin@admin.com"));
    assertTrue(url.contains("secret=JBSWY3DPEHPK3PXP"));
    assertTrue(url.contains("issuer=Inmobiliaria"));
  }

  @Test
  void shouldGenerateQrCodeDataUri() {
    String url =
        totpService.generateOtpAuthUrl("Inmobiliaria", "admin@admin.com", "JBSWY3DPEHPK3PXP");
    String qrDataUri = totpService.generateQrCodeDataUri(url);
    assertNotNull(qrDataUri);
    assertTrue(qrDataUri.startsWith("data:image/png;base64,"));
  }

  @Test
  void shouldRejectInvalidCode() {
    String secret = totpService.generateSecret();
    assertFalse(totpService.verifyCode(secret, "000000"));
    assertFalse(totpService.verifyCode(secret, "invalid"));
    assertFalse(totpService.verifyCode(null, "123456"));
  }
}

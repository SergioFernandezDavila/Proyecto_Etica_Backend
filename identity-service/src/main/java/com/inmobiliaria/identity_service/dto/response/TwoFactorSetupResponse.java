package com.inmobiliaria.identity_service.dto.response;

public record TwoFactorSetupResponse(String secretKey, String qrCodeDataUri, String otpAuthUrl) {}

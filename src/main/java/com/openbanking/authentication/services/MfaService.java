package com.openbanking.authentication.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class MfaService {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public String generateSecret() {
        // Generate the secret for the app and QR code
        return gAuth.createCredentials().getKey();
    }

    public String generateQrImageUrl(String keyId, String base32Secret) {
        try {
            String encodedLabel = URLEncoder.encode(keyId, StandardCharsets.UTF_8);
            encodedLabel = encodedLabel.replace("+", "%20");
            String otpAuthUrl = String.format("otpauth://totp/%s?secret=%s&digits=6", encodedLabel, base32Secret);
            // generate QR code image
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            generateQrCode(otpAuthUrl, baos);
            String base64Image = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    public boolean check(String key, int code) {
        try {
            //String secret = new String(this.bytesEncryptor.decrypt(Hex.decode(key)), StandardCharsets.UTF_8);
            return gAuth.authorize(key, code);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    // Helper method to generate QR code PNG bytes
    private void generateQrCode(String text, ByteArrayOutputStream outputStream) throws IOException {
        try {
            com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            int width = 200;
            int height = 200;
            var bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        } catch (com.google.zxing.WriterException e) {
            throw new IOException("QR Code generation failed", e);
        }
    }
}

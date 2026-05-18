package com.openbanking.authentication.util;

import com.openbanking.authentication.config.EncryptionConfigHolder;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Converter
public class EncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    private SecretKeySpec getKey() {
        String secret = EncryptionConfigHolder.getSecret();
        if (secret == null) {
            throw new IllegalStateException("Encryption secret is not initialized.");
        }
        return new SecretKeySpec(secret.getBytes(), "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encryptedBytes = cipher.doFinal(attribute.getBytes());
            String secret = Base64.getEncoder().encodeToString(encryptedBytes);
            return secret;
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting attribute", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            byte[] decodedBytes = Base64.getDecoder().decode(dbData);
            String secret = new String(cipher.doFinal(decodedBytes));
            return secret;
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting attribute", e);
        }
    }
}
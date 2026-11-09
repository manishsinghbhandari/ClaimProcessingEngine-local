package io.coredge.health.claims.payments.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CryptoUtils {
    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static String SECRET_KEY;

    @Value("${AES_SECRET_KEY}")
    public void setSecretKey(String secretKey) {
        SECRET_KEY = secretKey;
    }

    private static SecretKey getKey() {
        if (SECRET_KEY == null) {
            throw new IllegalStateException(
                    "CryptoUtils: SECRET_KEY has not been initialized. Ensure it is defined in application.properties or environment variable AES_SECRET_KEY.");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = sha.digest(SECRET_KEY.getBytes());
            return new SecretKeySpec(key, AES);
        } catch (Exception e) {
            throw new RuntimeException("Error generating secret key", e);
        }
    }

    public static String encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), spec);

        byte[] encrypted = cipher.doFinal(plaintext.getBytes());
        byte[] encryptedWithIv = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
        System.arraycopy(encrypted, 0, encryptedWithIv, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(encryptedWithIv);
    }

    public static String decrypt(String encryptedText) throws Exception {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];

        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        return new String(cipher.doFinal(encrypted));
    }
}

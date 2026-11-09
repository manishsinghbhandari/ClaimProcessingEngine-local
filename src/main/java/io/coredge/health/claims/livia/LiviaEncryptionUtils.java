package io.coredge.health.claims.livia;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class LiviaEncryptionUtils {

    private static final Logger logger = LoggerFactory.getLogger(LiviaEncryptionUtils.class);

    @Value("${liviaEncryptionKey}")
    private String liviaEncryptionKey;

    @Value("${liviaIv}")
    private String liviaIv;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public String decrypt(String responseData) throws Exception {
        try{
            // Parse the responseData string into a JsonObject
            JsonObject responseBody = JsonParser.parseString(responseData).getAsJsonObject();

            // Extract the "data" field from the JsonObject
            String data = responseBody.get("data").getAsString();
            IvParameterSpec ivParameterSpec = new IvParameterSpec(liviaIv.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKeySpec = new SecretKeySpec(liviaEncryptionKey.getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decodedEncryptedData = Base64.getDecoder().decode(data);
            byte[] original = cipher.doFinal(decodedEncryptedData);

            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error occurred while decrypting data: {}", e.getMessage());
            throw new RuntimeException("Error occurred while decrypting data" + e.getMessage());
        }
    }
}

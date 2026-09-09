package iuh.fit.paymentservice.infrastructure.security;

import iuh.fit.paymentservice.application.dto.request.security.EncryptedPayloadCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.EncryptedPayloadPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@Component
@Slf4j
public class RsaAesPayloadAdapter implements EncryptedPayloadPort {
    private static final int RSA_KEY_SIZE = 2048;
    private static final int AES_KEY_LENGTH = 32;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(RSA_KEY_SIZE);
            keyPair = keyPairGenerator.generateKeyPair();
            log.info("RSA key pair initialized for encrypted payment payloads");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize payment payload encryption", exception);
        }
    }

    @Override
    public String getPublicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Override
    public String decrypt(EncryptedPayloadCommand command) {
        if (command == null || isBlank(command.encryptedKey())
                || isBlank(command.iv()) || isBlank(command.encryptedData())) {
            throw invalidPayload(null);
        }
        try {
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] encodedAesKey = rsaCipher.doFinal(Base64.getDecoder().decode(command.encryptedKey()));
            byte[] aesKey = Base64.getDecoder().decode(new String(encodedAesKey, StandardCharsets.UTF_8));
            byte[] iv = Base64.getDecoder().decode(command.iv());
            byte[] encryptedData = Base64.getDecoder().decode(command.encryptedData());
            if (aesKey.length != AES_KEY_LENGTH || iv.length != GCM_IV_LENGTH) {
                throw invalidPayload(null);
            }

            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            aesCipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv)
            );
            return new String(aesCipher.doFinal(encryptedData), StandardCharsets.UTF_8);
        } catch (InvalidPaymentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPayload(exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private InvalidPaymentException invalidPayload(Exception exception) {
        if (exception == null) {
            log.warn("Encrypted bank account payload is incomplete");
        } else {
            log.warn("Cannot decrypt bank account payload: {}", exception.getClass().getSimpleName());
        }
        return new InvalidPaymentException(
                "ENCRYPTED_PAYLOAD_INVALID",
                "Dữ liệu tài khoản ngân hàng không thể giải mã hoặc đã bị thay đổi"
        );
    }
}

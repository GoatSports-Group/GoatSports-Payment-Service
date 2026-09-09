package iuh.fit.paymentservice.infrastructure.security;

import iuh.fit.paymentservice.application.dto.request.security.EncryptedPayloadCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaAesPayloadAdapterTests {
    private RsaAesPayloadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RsaAesPayloadAdapter();
        adapter.init();
    }

    @Test
    void decryptsPayloadEncryptedByBrowserCompatibleProtocol() throws Exception {
        String payload = "{\"bankBin\":\"970415\",\"accountNumber\":\"0123456789\","
                + "\"accountName\":\"NGUYEN VAN A\"}";

        EncryptedPayloadCommand encrypted = encrypt(payload);

        assertThat(adapter.decrypt(encrypted)).isEqualTo(payload);
    }

    @Test
    void rejectsPayloadWhenCipherTextWasModified() throws Exception {
        EncryptedPayloadCommand encrypted = encrypt("{\"accountNumber\":\"0123456789\"}");
        byte[] modified = Base64.getDecoder().decode(encrypted.encryptedData());
        modified[0] ^= 1;

        EncryptedPayloadCommand tampered = new EncryptedPayloadCommand(
                encrypted.encryptedKey(), encrypted.iv(), Base64.getEncoder().encodeToString(modified)
        );

        assertThatThrownBy(() -> adapter.decrypt(tampered))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("không thể giải mã");
    }

    private EncryptedPayloadCommand encrypt(String payload) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey aesKey = keyGenerator.generateKey();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        byte[] encryptedData = aesCipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(adapter.getPublicKey()))
        );
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedKey = rsaCipher.doFinal(
                Base64.getEncoder().encodeToString(aesKey.getEncoded()).getBytes(StandardCharsets.UTF_8)
        );

        return new EncryptedPayloadCommand(
                Base64.getEncoder().encodeToString(encryptedKey),
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encryptedData)
        );
    }
}

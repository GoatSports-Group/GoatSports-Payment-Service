package iuh.fit.paymentservice.infrastructure.security;

import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.security.BankAccountProtector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AesBankAccountProtector implements BankAccountProtector {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final String configuredSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesBankAccountProtector(@Value("${bank.account.encryption-key:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    @Override
    public String encrypt(String accountNumber) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw cryptoError(exception);
        }
    }

    @Override
    public String decrypt(String encryptedAccountNumber) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedAccountNumber);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw cryptoError(exception);
        }
    }

    @Override
    public String hash(String bankBin, String accountNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes(), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((bankBin + ":" + accountNumber).getBytes(StandardCharsets.UTF_8))
            );
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw cryptoError(exception);
        }
    }

    private SecretKeySpec aesKey() throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secretBytes()), "AES");
    }

    private byte[] secretBytes() {
        if (configuredSecret == null || configuredSecret.length() < 32) {
            throw new PaymentProviderException(
                    "BANK_ACCOUNT_ENCRYPTION_NOT_CONFIGURED",
                    "BANK_ACCOUNT_ENCRYPTION_KEY phải có ít nhất 32 ký tự"
            );
        }
        return configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    private PaymentProviderException cryptoError(Exception exception) {
        return new PaymentProviderException(
                "BANK_ACCOUNT_ENCRYPTION_ERROR",
                "Không thể bảo vệ thông tin tài khoản ngân hàng",
                exception
        );
    }
}

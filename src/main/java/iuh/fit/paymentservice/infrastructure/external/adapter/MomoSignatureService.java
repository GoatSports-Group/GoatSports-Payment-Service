package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class MomoSignatureService {
    public String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new PaymentProviderException(
                    "MOMO_SIGNATURE_ERROR",
                    "Không thể xử lý chữ ký MoMo",
                    exception
            );
        }
    }

    public String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new PaymentProviderException(
                    "MOMO_HASH_ERROR",
                    "Không thể tạo hash cho payload MoMo",
                    exception
            );
        }
    }

    public boolean matches(String expected, String supplied) {
        return expected != null
                && supplied != null
                && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)
        );
    }
}

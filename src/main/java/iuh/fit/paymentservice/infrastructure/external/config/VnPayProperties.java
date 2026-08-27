package iuh.fit.paymentservice.infrastructure.external.config;

import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.provider.vnpay")
public class VnPayProperties {
    private boolean enabled;
    private String paymentUrl;
    private String returnUrl;
    private String tmnCode;
    private String hashSecret;
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
    private int expirationMinutes = 15;

    @PostConstruct
    void validateWhenEnabled() {
        if (!enabled) {
            return;
        }
        if (isBlank(paymentUrl) || !hasScheme(paymentUrl, "https")) {
            throw configurationError("VNPAY_PAYMENT_URL phải là HTTPS URL do VNPAY cung cấp");
        }
        if (isBlank(returnUrl) || returnUrl.length() > 255 || !hasHttpScheme(returnUrl)) {
            throw configurationError("VNPAY_RETURN_URL phải là HTTP(S) URL hợp lệ");
        }
        if (isBlank(tmnCode) || !tmnCode.matches("[A-Za-z0-9]{8}")) {
            throw configurationError("VNPAY_TMN_CODE phải gồm đúng 8 ký tự chữ hoặc số");
        }
        if (isBlank(hashSecret)) {
            throw configurationError("VNPAY_HASH_SECRET chưa được cấu hình");
        }
        if (!"2.1.0".equals(version)) {
            throw configurationError("Payment Service chỉ hỗ trợ VNPAY_VERSION=2.1.0");
        }
        if (!"pay".equals(command)) {
            throw configurationError("VNPAY command phải là pay");
        }
        if (isBlank(orderType) || !orderType.matches("[A-Za-z0-9]{1,100}")) {
            throw configurationError("VNPAY_ORDER_TYPE không hợp lệ");
        }
        if (expirationMinutes < 1 || expirationMinutes > 1_440) {
            throw configurationError("VNPAY_EXPIRATION_MINUTES phải nằm trong khoảng 1-1440");
        }
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new PaymentProviderException(
                    "VNPAY_DISABLED",
                    "Kênh thanh toán VNPAY chưa được bật"
            );
        }
    }

    private boolean hasScheme(String value, String expectedScheme) {
        try {
            return expectedScheme.equalsIgnoreCase(URI.create(value).getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasHttpScheme(String value) {
        return hasScheme(value, "http") || hasScheme(value, "https");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PaymentProviderException configurationError(String message) {
        return new PaymentProviderException("VNPAY_CONFIGURATION_ERROR", message);
    }
}

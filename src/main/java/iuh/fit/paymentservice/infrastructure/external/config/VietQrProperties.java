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
@ConfigurationProperties(prefix = "payment.provider.vietqr")
public class VietQrProperties {
    private boolean enabled;
    private String apiUrl = "https://api.vietqr.io/v2/generate";
    private String quickLinkBaseUrl = "https://img.vietqr.io/image";
    private String clientId;
    private String apiKey;
    private String accountNo;
    private String accountName;
    private String acqId;
    private String template = "compact2";
    private int timeoutSeconds = 30;

    @PostConstruct
    void validateWhenEnabled() {
        if (!enabled) {
            return;
        }
        if (!isHttpsUrl(apiUrl)) {
            throw configurationError("VIETQR_API_URL phải là HTTPS URL hợp lệ");
        }
        if (!isHttpsUrl(quickLinkBaseUrl)) {
            throw configurationError("VIETQR_QUICK_LINK_BASE_URL phải là HTTPS URL hợp lệ");
        }
        if (isBlank(clientId) != isBlank(apiKey)) {
            throw configurationError("VIETQR_CLIENT_ID và VIETQR_API_KEY phải được cấu hình cùng nhau");
        }
        if (accountNo == null || !accountNo.matches("\\d{6,19}")) {
            throw configurationError("VIETQR_ACCOUNT_NO phải gồm 6-19 chữ số");
        }
        if (accountName == null || !accountName.matches("[A-Z0-9 ]{5,50}")) {
            throw configurationError("VIETQR_ACCOUNT_NAME phải viết hoa, không dấu, dài 5-50 ký tự");
        }
        if (acqId == null || !acqId.matches("\\d{6}")) {
            throw configurationError("VIETQR_ACQ_ID phải là mã BIN gồm 6 chữ số");
        }
        if (!template.matches("compact2|compact|qr_only|print")) {
            throw configurationError("VIETQR_TEMPLATE không được hỗ trợ");
        }
        if (timeoutSeconds < 5 || timeoutSeconds > 120) {
            throw configurationError("VIETQR_TIMEOUT_SECONDS phải nằm trong khoảng 5-120 giây");
        }
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new PaymentProviderException(
                    "VIETQR_DISABLED",
                    "Kênh thanh toán VietQR chưa được bật"
            );
        }
    }

    public boolean hasApiCredentials() {
        return !isBlank(clientId) && !isBlank(apiKey);
    }

    private boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PaymentProviderException configurationError(String message) {
        return new PaymentProviderException("VIETQR_CONFIGURATION_ERROR", message);
    }
}

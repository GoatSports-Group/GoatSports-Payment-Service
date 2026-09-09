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
@ConfigurationProperties(prefix = "payment.provider.payos")
public class PayOsProperties {
    private boolean enabled;
    private boolean payoutEnabled;
    private String apiUrl;
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String payoutClientId;
    private String payoutApiKey;
    private String payoutChecksumKey;
    private String webhookUrl;
    private String returnUrl;
    private String cancelUrl;
    private int timeoutMs = 30_000;
    private int maxRetries = 2;
    private boolean registerWebhookOnStartup = true;

    @PostConstruct
    void validateWhenEnabled() {
        if (!enabled && !payoutEnabled) {
            return;
        }
        if (!isHttpsUrl(apiUrl)) {
            throw configurationError("PAYOS_API_URL phải là HTTPS URL hợp lệ khi bật payOS");
        }
        if (enabled && (isBlank(clientId) || isBlank(apiKey) || isBlank(checksumKey))) {
            throw configurationError("PAYOS_CLIENT_ID, PAYOS_API_KEY và PAYOS_CHECKSUM_KEY là bắt buộc");
        }
        if (payoutEnabled && (isBlank(payoutClientId) || isBlank(payoutApiKey) || isBlank(payoutChecksumKey))) {
            throw configurationError(
                    "PAYOS_PAYOUT_CLIENT_ID, PAYOS_PAYOUT_API_KEY và PAYOS_PAYOUT_CHECKSUM_KEY là bắt buộc khi bật payout"
            );
        }
        if (enabled && !isHttpsUrl(webhookUrl)) {
            throw configurationError("PAYOS_WEBHOOK_URL phải là HTTPS URL công khai");
        }
        if (enabled && (!isHttpUrl(returnUrl) || !isHttpUrl(cancelUrl))) {
            throw configurationError("PAYOS_RETURN_URL và PAYOS_CANCEL_URL phải là HTTP(S) URL hợp lệ");
        }
        if (timeoutMs < 5_000 || timeoutMs > 120_000) {
            throw configurationError("PAYOS_TIMEOUT_MS phải nằm trong khoảng 5000-120000");
        }
        if (maxRetries < 0 || maxRetries > 5) {
            throw configurationError("PAYOS_MAX_RETRIES phải nằm trong khoảng 0-5");
        }
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new PaymentProviderException("PAYOS_DISABLED", "Kênh thanh toán payOS chưa được bật");
        }
    }

    public void requirePayoutEnabled() {
        if (!payoutEnabled) {
            throw new PaymentProviderException(
                    "PAYOS_PAYOUT_DISABLED",
                    "Kênh chuyển tiền payOS chưa được bật"
            );
        }
    }

    private boolean isHttpsUrl(String value) {
        return isUrl(value, true);
    }

    private boolean isHttpUrl(String value) {
        return isUrl(value, false);
    }

    private boolean isUrl(String value, boolean httpsOnly) {
        try {
            URI uri = URI.create(value);
            boolean schemeValid = httpsOnly
                    ? "https".equalsIgnoreCase(uri.getScheme())
                    : "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            return schemeValid && uri.getHost() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PaymentProviderException configurationError(String message) {
        return new PaymentProviderException("PAYOS_CONFIGURATION_ERROR", message);
    }
}

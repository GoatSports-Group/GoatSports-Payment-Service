package iuh.fit.paymentservice.infrastructure.external.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Component
@RequiredArgsConstructor
public class PayOsPayoutClientProvider {
    private final PayOsProperties properties;
    private volatile PayOS client;

    public PayOS get() {
        properties.requirePayoutEnabled();
        PayOS current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new PayOS(ClientOptions.builder()
                            .clientId(properties.getPayoutClientId())
                            .apiKey(properties.getPayoutApiKey())
                            .checksumKey(properties.getPayoutChecksumKey())
                            .baseURL(properties.getApiUrl())
                            .timeoutMs(properties.getTimeoutMs())
                            .maxRetries(properties.getMaxRetries())
                            .build());
                    client = current;
                }
            }
        }
        return current;
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}

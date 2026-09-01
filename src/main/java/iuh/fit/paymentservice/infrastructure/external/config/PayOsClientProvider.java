package iuh.fit.paymentservice.infrastructure.external.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

@Component
@RequiredArgsConstructor
public class PayOsClientProvider {
    private final PayOsProperties properties;
    private volatile PayOS client;

    public PayOS get() {
        properties.requireEnabled();
        PayOS current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    ClientOptions options = ClientOptions.builder()
                            .clientId(properties.getClientId())
                            .apiKey(properties.getApiKey())
                            .checksumKey(properties.getChecksumKey())
                            .baseURL(properties.getApiUrl())
                            .timeoutMs(properties.getTimeoutMs())
                            .maxRetries(properties.getMaxRetries())
                            .build();
                    current = new PayOS(options);
                    client = current;
                }
            }
        }
        return current;
    }

    @PreDestroy
    void close() {
        PayOS current = client;
        if (current != null) {
            current.close();
        }
    }
}

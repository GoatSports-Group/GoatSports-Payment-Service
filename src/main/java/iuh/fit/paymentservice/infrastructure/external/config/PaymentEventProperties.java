package iuh.fit.paymentservice.infrastructure.external.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.events")
public class PaymentEventProperties {
    private boolean publisherEnabled;
    private String topic;
    private int sendTimeoutSeconds = 10;

    @PostConstruct
    void validate() {
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("PAYMENT_EVENTS_TOPIC chưa được cấu hình");
        }
        if (sendTimeoutSeconds < 1 || sendTimeoutSeconds > 60) {
            throw new IllegalStateException("PAYMENT_EVENTS_SEND_TIMEOUT_SECONDS phải nằm trong khoảng 1-60");
        }
    }
}

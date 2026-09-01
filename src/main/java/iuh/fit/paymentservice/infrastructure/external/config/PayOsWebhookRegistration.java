package iuh.fit.paymentservice.infrastructure.external.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayOsWebhookRegistration {
    private final PayOsProperties properties;
    private final PayOsClientProvider clientProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (!properties.isEnabled() || !properties.isRegisterWebhookOnStartup()) {
            return;
        }
        try {
            clientProvider.get().webhooks().confirm(properties.getWebhookUrl());
            log.info("payOS webhook registration confirmed");
        } catch (RuntimeException exception) {
            log.error("Unable to confirm payOS webhook URL", exception);
        }
    }
}

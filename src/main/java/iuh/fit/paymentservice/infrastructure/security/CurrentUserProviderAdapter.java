package iuh.fit.paymentservice.infrastructure.security;

import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentUserProviderAdapter implements CurrentUserProvider {
    private final SecurityContextHelper securityContextHelper;

    @Override
    public Optional<String> getCurrentUserId() {
        return securityContextHelper.getCurrentUserId();
    }
}

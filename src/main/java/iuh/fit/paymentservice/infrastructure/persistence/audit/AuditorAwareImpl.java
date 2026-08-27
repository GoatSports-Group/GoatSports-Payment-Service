package iuh.fit.paymentservice.infrastructure.persistence.audit;

import iuh.fit.paymentservice.infrastructure.security.SecurityContextHelper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditorAware")
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<String> {
    private final SecurityContextHelper securityContextHelper;

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        return securityContextHelper.getCurrentUserId().or(() -> Optional.of("system"));
    }
}

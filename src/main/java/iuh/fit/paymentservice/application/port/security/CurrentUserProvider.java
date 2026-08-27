package iuh.fit.paymentservice.application.port.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<String> getCurrentUserId();
}

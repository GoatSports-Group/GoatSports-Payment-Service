package iuh.fit.paymentservice.application.port.security;

import iuh.fit.paymentservice.application.dto.request.security.EncryptedPayloadCommand;

public interface EncryptedPayloadPort {
    String getPublicKey();

    String decrypt(EncryptedPayloadCommand command);
}

package iuh.fit.paymentservice.application.dto.request.security;

public record EncryptedPayloadCommand(
        String encryptedKey,
        String iv,
        String encryptedData
) {
}

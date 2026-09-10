package iuh.fit.paymentservice.application.dto.request.payment;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentCommand {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final double MAX_AMOUNT = 99_999_999_999_999_999D;

    private UUID payerId;
    private PaymentReferenceType referenceType;
    private UUID referenceId;
    private PaymentPurpose purpose;
    private Double amount;
    private String currency;
    private String description;
    private String idempotencyKey;
    private LocalDateTime expiresAt;

    public ValidatedPayment validate() {
        if (payerId == null) {
            throw new InvalidPaymentException("PAYER_REQUIRED", "Không xác định được người thanh toán");
        }
        if (referenceType == null || referenceId == null) {
            throw new InvalidPaymentException(
                    "REFERENCE_REQUIRED", "Reference type và reference ID là bắt buộc"
            );
        }
        if (purpose == null || !purpose.supports(referenceType)) {
            throw new InvalidPaymentException(
                    "INVALID_PAYMENT_PURPOSE", "Purpose không hợp lệ với reference type"
            );
        }
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new InvalidPaymentException("INVALID_AMOUNT", "Amount phải lớn hơn 0");
        }
        if (amount > MAX_AMOUNT) {
            throw new InvalidPaymentException("INVALID_AMOUNT", "Amount vượt quá giới hạn cho phép");
        }

        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"VND".equals(normalizedCurrency)) {
            throw new InvalidPaymentException("UNSUPPORTED_CURRENCY", "Payment hiện chỉ hỗ trợ VND");
        }

        String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedIdempotencyKey).matches()) {
            throw new InvalidPaymentException(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key phải dài 8-128 ký tự và chỉ chứa chữ, số, '.', '_', ':', '-'"
            );
        }
        String normalizedDescription = description == null ? null : description.trim();
        if (normalizedDescription != null && normalizedDescription.length() > 500) {
            throw new InvalidPaymentException("INVALID_DESCRIPTION", "Description dài tối đa 500 ký tự");
        }
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new InvalidPaymentException("INVALID_EXPIRES_AT", "ExpiresAt phải nằm trong tương lai");
        }
        return new ValidatedPayment(
                amount, normalizedCurrency, normalizedDescription, normalizedIdempotencyKey
        );
    }

    public record ValidatedPayment(
            Double amount,
            String currency,
            String description,
            String idempotencyKey
    ) {
    }
}

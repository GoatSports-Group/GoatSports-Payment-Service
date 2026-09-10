package iuh.fit.paymentservice.application.dto.request.refund;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRefundCommand {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private UUID paymentId;
    private UUID requestedBy;
    private String reason;
    private Double amount;
    private String idempotencyKey;
    private Boolean autoDisburse;

    public void validate() {
        if (paymentId == null || requestedBy == null || amount == null || autoDisburse == null) {
            throw new InvalidPaymentException(
                    "REFUND_REQUEST_INVALID", "Payment ID, requestedBy và amount là bắt buộc"
            );
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new InvalidPaymentException("REFUND_AMOUNT_INVALID", "Số tiền refund phải lớn hơn 0");
        }
        try {
            BigDecimal.valueOf(amount).setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidPaymentException(
                    "REFUND_AMOUNT_INVALID", "Số tiền refund phải là số nguyên VND"
            );
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new InvalidPaymentException(
                    "REFUND_REASON_INVALID", "Lý do refund phải có từ 1 đến 500 ký tự"
            );
        }
        if (idempotencyKey == null
                || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey.trim()).matches()) {
            throw new InvalidPaymentException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key phải dài 8-128 ký tự và chỉ chứa chữ, số, '.', '_', ':', '-'"
            );
        }
    }
}

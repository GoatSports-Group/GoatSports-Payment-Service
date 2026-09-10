package iuh.fit.paymentservice.application.dto.request.attempt;

import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentAttemptCommand {
    private UUID paymentId;
    private UUID payerId;
    private PaymentMethod method;
    private String locale;
    private String ipAddress;

    public void validate() {
        if (paymentId == null || payerId == null) {
            throw new InvalidPaymentException(
                    "PAYMENT_ATTEMPT_REQUEST_INVALID", "Payment ID và payer ID là bắt buộc"
            );
        }
        if (method != PaymentMethod.BANK_TRANSFER) {
            throw new InvalidPaymentException(
                    "UNSUPPORTED_PAYMENT_METHOD", "payOS chỉ hỗ trợ phương thức BANK_TRANSFER"
            );
        }
        if (ipAddress == null || ipAddress.isBlank() || ipAddress.length() > 45) {
            throw new InvalidPaymentException(
                    "PAYOS_INVALID_IP_ADDRESS", "Địa chỉ IP thanh toán không hợp lệ"
            );
        }
    }
}

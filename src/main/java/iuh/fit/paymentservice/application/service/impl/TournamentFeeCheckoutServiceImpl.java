package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.attempt.CreatePaymentAttemptCommand;
import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.dto.response.attempt.CreatePaymentAttemptResult;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.BookingRemainingPaymentRetryService;
import iuh.fit.paymentservice.application.service.PaymentAttemptService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.application.service.TournamentFeeCheckoutService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentMethod;
import iuh.fit.paymentservice.domain.enums.PaymentPurpose;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TournamentFeeCheckoutServiceImpl implements TournamentFeeCheckoutService {
    private final PaymentService paymentService;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentRepository paymentRepository;
    private final BookingRemainingPaymentRetryService retryService;

    @Override
    public BookingCheckoutResult create(Command command) {
        validate(command);
        Payment existing = paymentRepository.findByReference(
                PaymentReferenceType.TOURNAMENT_REGISTRATION, command.registrationId(), PaymentPurpose.TOURNAMENT_FEE
        ).orElse(null);

        Payment payment;
        if (existing == null) {
            CreatePaymentResult created = paymentService.createPayment(CreatePaymentCommand.builder()
                    .payerId(command.payerId())
                    .referenceType(PaymentReferenceType.TOURNAMENT_REGISTRATION)
                    .referenceId(command.registrationId())
                    .purpose(PaymentPurpose.TOURNAMENT_FEE)
                    .amount(command.amount())
                    .currency("VND")
                    .description(command.description())
                    .idempotencyKey("tournament-fee:" + command.registrationId())
                    .expiresAt(command.expiresAt())
                    .build());
            payment = paymentRepository.findById(created.getPayment().getPaymentId()).orElseThrow();
        } else {
            // Doi nguoi tra hoac muc phi giua chung la dau hieu sai lech: dung lai de kiem tra thu cong.
            if (!existing.getPayerId().equals(command.payerId())
                    || Math.abs(existing.getAmount() - command.amount()) >= 0.01) {
                throw new PaymentConflictException("TOURNAMENT_FEE_MISMATCH",
                        "Khoản lệ phí hiện có không khớp người đăng ký hoặc mức phí của giải");
            }
            payment = retryService.prepare(existing.getPaymentId(), command.expiresAt());
        }

        CreatePaymentAttemptResult attempt = paymentAttemptService.createPaymentAttempt(
                CreatePaymentAttemptCommand.builder()
                        .paymentId(payment.getPaymentId())
                        .payerId(command.payerId())
                        .method(PaymentMethod.BANK_TRANSFER)
                        .locale(command.locale() == null ? "vn" : command.locale())
                        .ipAddress(command.ipAddress())
                        .build()
        );
        Payment refreshed = paymentRepository.findById(payment.getPaymentId()).orElse(payment);
        return new BookingCheckoutResult(
                refreshed.getPaymentId(),
                refreshed.getStatus().name(),
                attempt.getPaymentAttempt().getCheckoutUrl(),
                attempt.getPaymentAttempt().getDeeplink(),
                attempt.getPaymentAttempt().getQrCodeContent(),
                refreshed.getExpiresAt()
        );
    }

    private void validate(Command command) {
        if (command.payerId() == null || command.registrationId() == null) {
            throw new InvalidPaymentException("TOURNAMENT_FEE_INVALID", "Thiếu người đăng ký hoặc mã đăng ký giải");
        }
        if (command.amount() == null || command.amount() <= 0) {
            throw new InvalidPaymentException("TOURNAMENT_FEE_INVALID", "Giải miễn phí không cần thanh toán");
        }
        if (command.expiresAt() == null || !command.expiresAt().isAfter(LocalDateTime.now())) {
            throw new PaymentConflictException("TOURNAMENT_FEE_EXPIRED", "Đã quá hạn đóng lệ phí của đăng ký này");
        }
    }
}

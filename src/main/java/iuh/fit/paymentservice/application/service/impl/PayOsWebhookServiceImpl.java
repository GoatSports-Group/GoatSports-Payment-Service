package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.webhook.PayOsWebhookCommand;
import iuh.fit.paymentservice.application.dto.response.webhook.VerifiedPayOsWebhook;
import iuh.fit.paymentservice.application.port.client.PayOsWebhookVerificationPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.ProviderWebhookEventRepository;
import iuh.fit.paymentservice.application.service.PayOsWebhookService;
import iuh.fit.paymentservice.application.service.PaymentDomainEventService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.domain.enums.PaymentAttemptStatus;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.WebhookProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOsWebhookServiceImpl implements PayOsWebhookService {
    private final PayOsWebhookVerificationPort verificationPort;
    private final ProviderWebhookEventRepository webhookEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDomainEventService paymentDomainEventService;

    /**
     * Xác nhận thanh toán tự động từ webhook payOS. Thứ tự kiểm tra có chủ đích:
     * 1) verify chữ ký trước khi tin bất kỳ trường nào (webhook là endpoint công khai, ai cũng gọi được);
     * 2) chống xử lý trùng theo providerEventId — payOS gửi lại webhook khi timeout;
     * 3) khóa attempt + payment (FOR UPDATE) rồi đối chiếu orderCode, số tiền, tiền tệ với dữ liệu của chính mình,
     *    không bao giờ lấy số tiền từ webhook làm số tiền đã thu.
     */
    @Override
    @Transactional
    public void process(PayOsWebhookCommand command) {
        VerifiedPayOsWebhook verified = verificationPort.verify(command);
        String payloadHash = verificationPort.payloadHash(command);
        String providerEventId = providerEventId(verified, payloadHash);
        if (webhookEventRepository.findByProviderAndProviderEventId(
                PaymentProvider.PAYOS,
                providerEventId
        ).isPresent()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentAttempt attempt = paymentAttemptRepository
                .findByProviderAndProviderTransactionIdForUpdate(
                        PaymentProvider.PAYOS,
                        verified.paymentLinkId()
                )
                .orElse(null);

        ProviderWebhookEvent event = webhookEventRepository.save(ProviderWebhookEvent.builder()
                .attemptId(attempt == null ? null : attempt.getAttemptId())
                .provider(PaymentProvider.PAYOS)
                .providerEventId(providerEventId)
                .payloadHash(payloadHash)
                .signatureValid(true)
                .status(WebhookProcessingStatus.RECEIVED)
                .receivedAt(now)
                .build());

        // Lưu sự kiện kể cả khi bỏ qua/từ chối: vừa là dấu chống trùng, vừa để đối soát khi có khiếu nại.
        if (attempt == null) {
            complete(event, WebhookProcessingStatus.IGNORED,
                    "Không tìm thấy attempt; có thể là webhook mẫu khi đăng ký", now);
            return;
        }

        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPaymentId()).orElse(null);
        String validationError = findProcessingError(command, verified, attempt, payment);
        if (validationError != null) {
            log.warn("Rejected verified payOS webhook {}: {}", providerEventId, validationError);
            complete(event, WebhookProcessingStatus.FAILED, validationError, now);
            return;
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
            complete(event, WebhookProcessingStatus.IGNORED,
                    "Payment đã được xác nhận trước đó", now);
            return;
        }

        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        attempt.setFailureCode(null);
        attempt.setFailureReason(null);
        attempt.setCompletedAt(now);
        attempt = paymentAttemptRepository.save(attempt);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(now);
        payment = paymentRepository.save(payment);
        paymentDomainEventService.recordPaymentResult(payment, attempt, payment.getPayerId());
        complete(event, WebhookProcessingStatus.PROCESSED, null, now);
        log.info("Payment {} confirmed automatically by payOS webhook", payment.getPaymentId());
    }

    private String findProcessingError(
            PayOsWebhookCommand command,
            VerifiedPayOsWebhook verified,
            PaymentAttempt attempt,
            Payment payment
    ) {
        if (!Boolean.TRUE.equals(command.success()) || !"00".equals(verified.code())) {
            return "Webhook payOS không biểu thị giao dịch thành công";
        }
        if (payment == null) {
            return "Không tìm thấy payment của attempt";
        }
        if (verified.orderCode() == null
                || verified.orderCode() != PaymentAttemptServiceImpl.providerOrderCode(attempt.getAttemptId())) {
            return "orderCode payOS không khớp attempt";
        }
        if (verified.amount() == null || verified.amount() != providerAmount(payment.getAmount())) {
            return "Số tiền payOS không khớp payment";
        }
        if (!"VND".equalsIgnoreCase(verified.currency())) {
            return "Tiền tệ payOS không hợp lệ";
        }
        if (payment.getStatus() != PaymentStatus.CREATED
                && payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.SUCCEEDED) {
            return "Payment không còn ở trạng thái có thể xác nhận";
        }
        if (attempt.getStatus() != PaymentAttemptStatus.CREATED
                && attempt.getStatus() != PaymentAttemptStatus.REDIRECT_READY
                && attempt.getStatus() != PaymentAttemptStatus.PENDING
                && attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED) {
            return "Attempt không còn ở trạng thái có thể xác nhận";
        }
        return null;
    }

    /** payOS tính bằng đồng (số nguyên); số tiền có phần lẻ thì coi như không khớp thay vì làm tròn. */
    private long providerAmount(Double amount) {
        try {
            return BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (RuntimeException exception) {
            return Long.MIN_VALUE;
        }
    }

    private String providerEventId(VerifiedPayOsWebhook webhook, String payloadHash) {
        String reference = webhook.reference();
        if (reference == null || reference.isBlank()) {
            return "HASH:" + payloadHash;
        }
        return reference.length() <= 200 ? reference : reference.substring(0, 200);
    }

    private void complete(
            ProviderWebhookEvent event,
            WebhookProcessingStatus status,
            String reason,
            LocalDateTime now
    ) {
        event.setStatus(status);
        event.setProcessedAt(now);
        event.setFailureReason(reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500));
        webhookEventRepository.save(event);
    }
}

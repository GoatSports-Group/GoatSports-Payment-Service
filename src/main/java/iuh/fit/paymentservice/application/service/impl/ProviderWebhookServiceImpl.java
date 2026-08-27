package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.webhook.MomoWebhookCommand;
import iuh.fit.paymentservice.application.dto.request.payment.ProviderPaymentResultCommand;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.MomoWebhookVerificationPort;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.port.persistence.ProviderWebhookEventRepository;
import iuh.fit.paymentservice.application.service.ProviderPaymentResultService;
import iuh.fit.paymentservice.application.service.ProviderWebhookService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.domain.entity.ProviderWebhookEvent;
import iuh.fit.paymentservice.domain.enums.PaymentProvider;
import iuh.fit.paymentservice.domain.enums.WebhookProcessingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderWebhookServiceImpl implements ProviderWebhookService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MomoWebhookVerificationPort webhookVerificationPort;
    private final ProviderWebhookEventRepository webhookEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderPaymentResultService providerPaymentResultService;

    @Override
    @Transactional(noRollbackFor = PaymentProviderException.class)
    public void processMomoWebhook(MomoWebhookCommand command) {
        LocalDateTime now = LocalDateTime.now();
        String payloadHash = webhookVerificationPort.payloadHash(command);
        String providerEventId = providerEventId(command, payloadHash);
        if (webhookEventRepository.findByProviderAndProviderEventId(
                PaymentProvider.MOMO,
                providerEventId
        ).isPresent()) {
            return;
        }

        boolean signatureValid = webhookVerificationPort.isSignatureValid(command);
        if (!signatureValid) {
            saveTerminalEvent(
                    null,
                    providerEventId,
                    payloadHash,
                    false,
                    WebhookProcessingStatus.FAILED,
                    "Chữ ký callback MoMo không hợp lệ",
                    now
            );
            return;
        }

        Optional<UUID> attemptId = parseAttemptId(command.getOrderId());
        Optional<PaymentAttempt> attempt = attemptId.flatMap(paymentAttemptRepository::findById);
        if (attempt.isEmpty()) {
            saveTerminalEvent(
                    null,
                    providerEventId,
                    payloadHash,
                    true,
                    WebhookProcessingStatus.FAILED,
                    "Không tìm thấy payment attempt từ orderId MoMo",
                    now
            );
            return;
        }

        ProviderWebhookEvent event = webhookEventRepository.save(ProviderWebhookEvent.builder()
                .attemptId(attempt.get().getAttemptId())
                .provider(PaymentProvider.MOMO)
                .providerEventId(providerEventId)
                .payloadHash(payloadHash)
                .signatureValid(true)
                .status(WebhookProcessingStatus.RECEIVED)
                .receivedAt(now)
                .build());

        String businessError = validateBusinessData(command, attempt.get());
        if (businessError != null) {
            completeEvent(event, WebhookProcessingStatus.FAILED, businessError, now);
            return;
        }

        try {
            boolean applied = providerPaymentResultService.apply(ProviderPaymentResultCommand.builder()
                    .attemptId(attempt.get().getAttemptId())
                    .providerTransactionId(String.valueOf(command.getTransId()))
                    .amount(command.getAmount())
                    .resultCode(command.getResultCode())
                    .message(command.getMessage())
                    .occurredAt(providerTime(command.getResponseTime()))
                    .build());
            completeEvent(
                    event,
                    applied ? WebhookProcessingStatus.PROCESSED : WebhookProcessingStatus.IGNORED,
                    applied ? null : "Callback MoMo không làm thay đổi trạng thái terminal hiện tại",
                    now
            );
        } catch (PaymentProviderException exception) {
            completeEvent(event, WebhookProcessingStatus.FAILED, exception.getMessage(), now);
        }
    }

    private String validateBusinessData(MomoWebhookCommand command, PaymentAttempt attempt) {
        if (!webhookVerificationPort.isExpectedPartnerCode(command.getPartnerCode())) {
            return "PartnerCode MoMo không khớp";
        }
        String expectedReference = providerReference(attempt.getAttemptId());
        if (!expectedReference.equals(command.getOrderId())
                || !expectedReference.equals(command.getRequestId())) {
            return "OrderId hoặc requestId MoMo không khớp payment attempt";
        }
        if (!"momo_wallet".equals(command.getOrderType())) {
            return "orderType MoMo không hợp lệ";
        }
        if (command.getResponseTime() == null || command.getResponseTime() <= 0) {
            return "responseTime MoMo không hợp lệ";
        }
        if ((command.getResultCode() == 0 || command.getResultCode() == 9000)
                && (command.getTransId() == null || command.getTransId() <= 0)) {
            return "Giao dịch MoMo thành công nhưng thiếu transId";
        }
        Optional<Payment> payment = paymentRepository.findById(attempt.getPaymentId());
        if (payment.isEmpty()) {
            return "Không tìm thấy payment của callback MoMo";
        }
        if (command.getAmount() == null || providerAmount(payment.get().getAmount()) != command.getAmount()) {
            return "Số tiền callback MoMo không khớp payment";
        }
        String expectedOrderInfo = "GoatSports payment "
                + payment.get().getPaymentId().toString().replace("-", "");
        if (!expectedOrderInfo.equals(command.getOrderInfo())) {
            return "Nội dung callback MoMo không khớp payment";
        }
        return null;
    }

    private void saveTerminalEvent(
            UUID attemptId,
            String providerEventId,
            String payloadHash,
            boolean signatureValid,
            WebhookProcessingStatus status,
            String failureReason,
            LocalDateTime now
    ) {
        webhookEventRepository.save(ProviderWebhookEvent.builder()
                .attemptId(attemptId)
                .provider(PaymentProvider.MOMO)
                .providerEventId(providerEventId)
                .payloadHash(payloadHash)
                .signatureValid(signatureValid)
                .status(status)
                .receivedAt(now)
                .processedAt(now)
                .failureReason(truncate(failureReason, 500))
                .build());
    }

    private void completeEvent(
            ProviderWebhookEvent event,
            WebhookProcessingStatus status,
            String reason,
            LocalDateTime processedAt
    ) {
        event.setStatus(status);
        event.setProcessedAt(processedAt);
        event.setFailureReason(truncate(reason, 500));
        webhookEventRepository.save(event);
    }

    private String providerEventId(MomoWebhookCommand command, String payloadHash) {
        String transaction = command.getTransId() == null ? "NO_TRANS_ID" : command.getTransId().toString();
        String result = command.getResultCode() == null ? "NO_RESULT" : command.getResultCode().toString();
        String responseTime = command.getResponseTime() == null
                ? payloadHash.substring(0, 16)
                : command.getResponseTime().toString();
        return transaction + ":" + result + ":" + responseTime;
    }

    private Optional<UUID> parseAttemptId(String orderId) {
        if (orderId == null || !orderId.matches("[0-9a-fA-F]{32}")) {
            return Optional.empty();
        }
        String value = orderId.substring(0, 8)
                + "-" + orderId.substring(8, 12)
                + "-" + orderId.substring(12, 16)
                + "-" + orderId.substring(16, 20)
                + "-" + orderId.substring(20);
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String providerReference(UUID attemptId) {
        return attemptId.toString().replace("-", "");
    }

    private long providerAmount(Double amount) {
        try {
            return BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (RuntimeException exception) {
            return Long.MIN_VALUE;
        }
    }

    private LocalDateTime providerTime(Long epochMillis) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), BUSINESS_ZONE);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException("MOMO_INVALID_RESPONSE_TIME", "responseTime MoMo không hợp lệ");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

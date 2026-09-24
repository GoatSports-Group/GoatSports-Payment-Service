package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentAccessDeniedException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentAttemptRepository;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import iuh.fit.paymentservice.application.service.PaymentCreationTransactionService;
import iuh.fit.paymentservice.application.service.PaymentService;
import iuh.fit.paymentservice.domain.entity.Payment;
import iuh.fit.paymentservice.domain.enums.PaymentStatus;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentCreationTransactionService paymentCreationTransactionService;

    /**
     * Tạo payment idempotent: cùng Idempotency-Key + cùng nội dung thì trả lại payment cũ (client gửi lại an toàn),
     * cùng key nhưng khác nội dung thì báo xung đột. Mỗi (reference, purpose) chỉ có MỘT payment — ví dụ một booking
     * chỉ có một khoản cọc — nên không thể thu hai lần cho cùng một mục đích.
     */
    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand command) {
        CreatePaymentCommand.ValidatedPayment validated = command.validate();
        String requestHash = requestHash(command, validated);

        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(validated.idempotencyKey());
        if (existingByKey.isPresent()) {
            return replayOrConflict(existingByKey.get(), requestHash);
        }

        Optional<Payment> existingByReference = paymentRepository.findByReference(
                command.getReferenceType(), command.getReferenceId(), command.getPurpose()
        );
        if (existingByReference.isPresent()) {
            Payment existing = existingByReference.get();
            if (existing.getIdempotencyKey().equals(validated.idempotencyKey())) {
                return replayOrConflict(existing, requestHash);
            }
            throw new PaymentConflictException(
                    "PAYMENT_ALREADY_EXISTS",
                    "Reference và purpose này đã có payment"
            );
        }

        Payment payment = Payment.builder()
                .payerId(command.getPayerId())
                .purpose(command.getPurpose())
                .referenceType(command.getReferenceType())
                .referenceId(command.getReferenceId())
                .amount(validated.amount())
                .platformFeeAmount(0D)
                .payeeAmount(validated.amount())
                .currency(validated.currency())
                .description(validated.description())
                .idempotencyKey(validated.idempotencyKey())
                .status(PaymentStatus.CREATED)
                .expiresAt(command.getExpiresAt())
                .build();

        try {
            Payment savedPayment = paymentCreationTransactionService.create(payment);
            return new CreatePaymentResult(paymentMapper.toPaymentResponseCommand(savedPayment), false);
        } catch (DataIntegrityViolationException exception) {
            // Hai request cùng lúc cùng qua bước kiểm tra ở trên: unique constraint của DB là chốt chặn cuối,
            // request thua đọc lại bản đã tạo để trả kết quả nhất quán.
            return resolveConcurrentCreate(command, validated.idempotencyKey(), requestHash);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseCommand getPayment(UUID paymentId, UUID payerUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (!payment.getPayerId().equals(payerUserId)) {
            throw new PaymentAccessDeniedException();
        }
        PaymentResponseCommand response = paymentMapper.toPaymentResponseCommand(payment);
        paymentAttemptRepository.findLatestByPaymentIds(List.of(paymentId)).stream()
                .findFirst()
                .ifPresent(attempt -> enrichAttempt(response, attempt));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseCommand> getPaymentsByReferences(
            PaymentReferenceType referenceType,
            Set<UUID> referenceIds
    ) {
        if (referenceType == null || referenceIds == null || referenceIds.isEmpty()) {
            throw new InvalidPaymentException(
                    "REFERENCE_REQUIRED",
                    "Reference type và ít nhất một reference ID là bắt buộc"
            );
        }
        if (referenceIds.size() > 100 || referenceIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPaymentException(
                    "INVALID_REFERENCE_QUERY",
                    "Mỗi truy vấn chỉ hỗ trợ tối đa 100 reference ID hợp lệ"
            );
        }
        List<Payment> payments = paymentRepository.findByReferences(referenceType, referenceIds);
        Map<UUID, PaymentAttempt> latestAttempts = new LinkedHashMap<>();
        paymentAttemptRepository.findLatestByPaymentIds(
                payments.stream().map(Payment::getPaymentId).toList()
        ).forEach(attempt -> latestAttempts.putIfAbsent(attempt.getPaymentId(), attempt));

        return payments.stream().map(payment -> {
            PaymentResponseCommand response = paymentMapper.toPaymentResponseCommand(payment);
            PaymentAttempt attempt = latestAttempts.get(payment.getPaymentId());
            if (attempt != null) {
                enrichAttempt(response, attempt);
            }
            return response;
        }).toList();
    }

    private void enrichAttempt(PaymentResponseCommand response, PaymentAttempt attempt) {
        response.setProvider(attempt.getProvider());
        response.setMethod(attempt.getMethod());
        response.setProviderTransactionId(attempt.getProviderTransactionId());
        response.setFailureCode(attempt.getFailureCode());
        response.setFailureReason(attempt.getFailureReason());
    }

    private CreatePaymentResult resolveConcurrentCreate(
            CreatePaymentCommand command,
            String idempotencyKey,
            String requestHash
    ) {
        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return replayOrConflict(existingByKey.get(), requestHash);
        }
        if (paymentRepository.findByReference(
                command.getReferenceType(), command.getReferenceId(), command.getPurpose()).isPresent()) {
            throw new PaymentConflictException(
                    "PAYMENT_ALREADY_EXISTS",
                    "Reference và purpose này đã có payment"
            );
        }
        throw new PaymentConflictException(
                "PAYMENT_CREATE_CONFLICT",
                "Payment được tạo đồng thời bởi một request khác"
        );
    }

    private CreatePaymentResult replayOrConflict(Payment existing, String requestHash) {
        if (!MessageDigest.isEqual(
                requestHash(existing).getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new PaymentConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key đã được dùng với payload khác"
            );
        }
        return new CreatePaymentResult(paymentMapper.toPaymentResponseCommand(existing), true);
    }

    private String requestHash(
            CreatePaymentCommand command,
            CreatePaymentCommand.ValidatedPayment validated
    ) {
        return sha256(String.join("|",
                command.getPayerId().toString(),
                command.getReferenceType().name(),
                command.getReferenceId().toString(),
                command.getPurpose().name(),
                Double.toString(validated.amount()),
                validated.currency(),
                nullable(validated.description()),
                nullable(command.getExpiresAt())
        ));
    }

    private String requestHash(Payment payment) {
        return sha256(String.join("|",
                payment.getPayerId().toString(),
                payment.getReferenceType().name(),
                payment.getReferenceId().toString(),
                payment.getPurpose().name(),
                Double.toString(payment.getAmount()),
                payment.getCurrency(),
                nullable(payment.getDescription()),
                nullable(payment.getExpiresAt())
        ));
    }

    private String nullable(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

}

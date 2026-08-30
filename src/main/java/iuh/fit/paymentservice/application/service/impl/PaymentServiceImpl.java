package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentAccessDeniedException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.mapper.PaymentMapper;
import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
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
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final double MAX_AMOUNT = 99_999_999_999_999_999D;

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentCreationTransactionService paymentCreationTransactionService;

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand command) {
        ValidatedPayment validated = validate(command);
        String requestHash = requestHash(command, validated);

        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(validated.idempotencyKey());
        if (existingByKey.isPresent()) {
            return replayOrConflict(existingByKey.get(), requestHash);
        }

        paymentRepository.findByReference(command.getReferenceType(), command.getReferenceId(), command.getPurpose())
                .ifPresent(existing -> {
                    throw new PaymentConflictException(
                            "PAYMENT_ALREADY_EXISTS",
                            "Reference và purpose này đã có payment"
                    );
                });

        Payment payment = Payment.builder()
                .payerId(command.getPayerId())
                .purpose(command.getPurpose())
                .referenceType(command.getReferenceType())
                .referenceId(command.getReferenceId())
                .amount(validated.amount())
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
        return paymentMapper.toPaymentResponseCommand(payment);
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
        if (referenceIds.size() > 100 || referenceIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidPaymentException(
                    "INVALID_REFERENCE_QUERY",
                    "Mỗi truy vấn chỉ hỗ trợ tối đa 100 reference ID hợp lệ"
            );
        }
        return paymentRepository.findByReferences(referenceType, referenceIds).stream()
                .map(paymentMapper::toPaymentResponseCommand)
                .toList();
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

    private ValidatedPayment validate(CreatePaymentCommand command) {
        if (command.getPayerId() == null) {
            throw new InvalidPaymentException("PAYER_REQUIRED", "Không xác định được người thanh toán");
        }
        if (command.getReferenceType() == null || command.getReferenceId() == null) {
            throw new InvalidPaymentException("REFERENCE_REQUIRED", "Reference type và reference ID là bắt buộc");
        }
        if (command.getPurpose() == null || !command.getPurpose().supports(command.getReferenceType())) {
            throw new InvalidPaymentException(
                    "INVALID_PAYMENT_PURPOSE",
                    "Purpose không hợp lệ với reference type"
            );
        }
        if (command.getAmount() == null || !Double.isFinite(command.getAmount()) || command.getAmount() <= 0) {
            throw new InvalidPaymentException("INVALID_AMOUNT", "Amount phải lớn hơn 0");
        }
        if (command.getAmount() > MAX_AMOUNT) {
            throw new InvalidPaymentException("INVALID_AMOUNT", "Amount vượt quá giới hạn cho phép");
        }

        String currency = command.getCurrency() == null
                ? ""
                : command.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!"VND".equals(currency)) {
            throw new InvalidPaymentException("UNSUPPORTED_CURRENCY", "Payment hiện chỉ hỗ trợ VND");
        }

        String idempotencyKey = command.getIdempotencyKey() == null
                ? ""
                : command.getIdempotencyKey().trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new InvalidPaymentException(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key phải dài 8-128 ký tự và chỉ chứa chữ, số, '.', '_', ':', '-'"
            );
        }
        String description = command.getDescription() == null ? null : command.getDescription().trim();
        if (description != null && description.length() > 500) {
            throw new InvalidPaymentException("INVALID_DESCRIPTION", "Description dài tối đa 500 ký tự");
        }
        if (command.getExpiresAt() != null && !command.getExpiresAt().isAfter(java.time.LocalDateTime.now())) {
            throw new InvalidPaymentException("INVALID_EXPIRES_AT", "ExpiresAt phải nằm trong tương lai");
        }
        return new ValidatedPayment(command.getAmount(), currency, description, idempotencyKey);
    }

    private String requestHash(CreatePaymentCommand command, ValidatedPayment validated) {
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

    private record ValidatedPayment(
            Double amount,
            String currency,
            String description,
            String idempotencyKey
    ) {
    }
}

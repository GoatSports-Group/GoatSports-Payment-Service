package iuh.fit.paymentservice.infrastructure.external.adapter;

import iuh.fit.paymentservice.application.dto.request.payout.CreatePayoutCommand;
import iuh.fit.paymentservice.application.dto.response.payout.ProviderPayoutResult;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import iuh.fit.paymentservice.application.port.client.PayoutProviderPort;
import iuh.fit.paymentservice.domain.enums.PayoutStatus;
import iuh.fit.paymentservice.infrastructure.external.config.PayOsPayoutClientProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.payos.exception.APIException;
import vn.payos.model.v1.payouts.PayoutApprovalState;
import vn.payos.model.v1.payouts.PayoutTransaction;
import vn.payos.model.v1.payouts.PayoutTransactionState;
import vn.payos.model.v1.payouts.batch.PayoutBatchItem;
import vn.payos.model.v1.payouts.batch.PayoutBatchRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayOsPayoutAdapter implements PayoutProviderPort {
    private final PayOsPayoutClientProvider clientProvider;

    @Override
    public ProviderPayoutResult create(CreatePayoutCommand command) {
        try {
            PayoutBatchItem item = PayoutBatchItem.builder()
                    .referenceId(command.referenceId())
                    .amount(amount(command.amount()))
                    .description(command.description())
                    .toBin(command.bankBin())
                    .toAccountNumber(command.accountNumber())
                    .build();
            PayoutBatchRequest request = PayoutBatchRequest.builder()
                    .referenceId(command.referenceId())
                    .validateDestination(true)
                    .category(List.of("GOATSPORTS_REFUND"))
                    .payouts(List.of(item))
                    .build();
            return map(clientProvider.get().payouts().batch().create(request, command.idempotencyKey()));
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (APIException exception) {
            throw providerException("create", exception);
        } catch (RuntimeException exception) {
            log.error(
                    "payOS payout create failed unexpectedly: {}: {}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            throw new PaymentProviderException(
                    "PAYOS_PAYOUT_ERROR",
                    "payOS không thể xác minh hoặc tạo giao dịch hoàn tiền",
                    exception
            );
        }
    }

    @Override
    public ProviderPayoutResult get(String providerPayoutId) {
        try {
            return map(clientProvider.get().payouts().get(providerPayoutId));
        } catch (APIException exception) {
            throw providerException("query", exception);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException("PAYOS_PAYOUT_QUERY_ERROR", "Không thể đối soát payout payOS", exception);
        }
    }

    private PaymentProviderException providerException(String operation, APIException exception) {
        String status = exception.getStatusCode().map(String::valueOf).orElse("UNKNOWN");
        String providerCode = exception.getErrorCode().filter(value -> !value.isBlank()).orElse(status);
        String description = exception.getErrorDesc()
                .filter(value -> !value.isBlank())
                .orElseGet(() -> exception.getMessage() == null ? "payOS payout request failed" : exception.getMessage());
        log.warn(
                "payOS payout {} rejected: status={}, code={}, description={}",
                operation,
                status,
                providerCode,
                description
        );
        String normalizedCode = providerCode.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
        return new PaymentProviderException("PAYOS_PAYOUT_" + normalizedCode, description, exception);
    }

    private ProviderPayoutResult map(vn.payos.model.v1.payouts.Payout payout) {
        if (payout == null || payout.getId() == null || payout.getTransactions() == null
                || payout.getTransactions().isEmpty()) {
            throw new PaymentProviderException("PAYOS_PAYOUT_INVALID_RESPONSE", "Phản hồi payout payOS không hợp lệ");
        }
        PayoutTransaction transaction = payout.getTransactions().getFirst();
        PayoutStatus status = status(payout.getApprovalState(), transaction.getState());
        return new ProviderPayoutResult(
                payout.getId(), transaction.getToAccountName(), status,
                transaction.getErrorCode(), transaction.getErrorMessage()
        );
    }

    private PayoutStatus status(PayoutApprovalState approval, PayoutTransactionState transaction) {
        if (transaction == PayoutTransactionState.SUCCEEDED) return PayoutStatus.SUCCEEDED;
        if (transaction == PayoutTransactionState.CANCELLED || approval == PayoutApprovalState.CANCELLED) {
            return PayoutStatus.CANCELLED;
        }
        if (transaction == PayoutTransactionState.FAILED || transaction == PayoutTransactionState.REVERSED
                || approval == PayoutApprovalState.FAILED || approval == PayoutApprovalState.REJECTED) {
            return PayoutStatus.FAILED;
        }
        return PayoutStatus.PROCESSING;
    }

    private long amount(Double value) {
        try {
            return BigDecimal.valueOf(value).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (RuntimeException exception) {
            throw new PaymentProviderException("PAYOS_INVALID_AMOUNT", "Số tiền payout không hợp lệ");
        }
    }
}

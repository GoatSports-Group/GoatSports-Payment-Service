package iuh.fit.paymentservice.infrastructure.exception;

import iuh.fit.paymentservice.application.dto.response.base.ApiErrorResponse;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.exception.PaymentAccessDeniedException;
import iuh.fit.paymentservice.application.exception.PaymentApplicationException;
import iuh.fit.paymentservice.application.exception.PaymentConflictException;
import iuh.fit.paymentservice.application.exception.PaymentNotFoundException;
import iuh.fit.paymentservice.application.exception.PaymentProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentException(
            PaymentApplicationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception) {
            case PaymentNotFoundException paymentNotFoundException -> HttpStatus.NOT_FOUND;
            case PaymentAccessDeniedException paymentAccessDeniedException -> HttpStatus.FORBIDDEN;
            case PaymentConflictException paymentConflictException -> HttpStatus.CONFLICT;
            case PaymentProviderException paymentProviderException ->
                    isConfigurationError(paymentProviderException)
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.BAD_GATEWAY;
            case InvalidPaymentException invalidPaymentException -> HttpStatus.BAD_REQUEST;
            case null, default -> HttpStatus.BAD_REQUEST;
        };

        assert exception != null;
        return error(status, exception.getCode(), exception.getMessage(), Map.of(), request);
    }

    private boolean isConfigurationError(PaymentProviderException exception) {
        return "VNPAY_DISABLED".equals(exception.getCode())
                || "VNPAY_CONFIGURATION_ERROR".equals(exception.getCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Dữ liệu payment không hợp lệ",
                details,
                request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        String code = "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())
                ? "IDEMPOTENCY_KEY_REQUIRED"
                : "HEADER_REQUIRED";
        return error(HttpStatus.BAD_REQUEST, code, exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "JSON hoặc enum trong request không hợp lệ",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Payment Service gặp lỗi không mong đợi",
                Map.of(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status)
                .header("X-Correlation-Id", correlationId)
                .body(new ApiErrorResponse(
                        status.value(),
                        code,
                        message,
                        correlationId,
                        details
                ));
    }
}

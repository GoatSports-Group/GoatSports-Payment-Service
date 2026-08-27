package iuh.fit.paymentservice.presentation.response;

import java.util.Map;

public record ApiErrorResponse(
        Integer statusCode,
        String code,
        String message,
        String correlationId,
        Map<String, Object> details
) {
}

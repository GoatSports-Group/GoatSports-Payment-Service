package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.dto.request.payment.CreateBookingCheckoutCommand;
import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.BookingCheckoutService;
import iuh.fit.paymentservice.presentation.request.CreateBookingCheckoutRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class BookingCheckoutInternalController {
    private final BookingCheckoutService bookingCheckoutService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/booking-checkouts")
    public ResponseEntity<BookingCheckoutResult> create(
            @Valid @RequestBody CreateBookingCheckoutRequest request,
            HttpServletRequest servletRequest
    ) {
        BookingCheckoutResult result = bookingCheckoutService.create(new CreateBookingCheckoutCommand(
                authenticatedPayerId(), request.getBookingId(), request.getAmount(),
                request.getCurrency(), request.getLocale(), clientIp(servletRequest)
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private UUID authenticatedPayerId() {
        String payerId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_PAYER_REQUIRED", "Venue Service không cung cấp payer ID hợp lệ"
                ));
        try {
            return UUID.fromString(payerId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException(
                    "AUTHENTICATED_PAYER_REQUIRED", "Venue Service không cung cấp payer ID hợp lệ"
            );
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

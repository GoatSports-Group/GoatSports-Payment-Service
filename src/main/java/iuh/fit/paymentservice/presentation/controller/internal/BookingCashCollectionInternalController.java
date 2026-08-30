package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.dto.request.payment.CollectBookingCashCommand;
import iuh.fit.paymentservice.application.exception.InvalidPaymentException;
import iuh.fit.paymentservice.application.port.security.CurrentUserProvider;
import iuh.fit.paymentservice.application.service.BookingCashCollectionService;
import iuh.fit.paymentservice.presentation.converter.PaymentConverter;
import iuh.fit.paymentservice.presentation.request.CollectBookingCashRequest;
import iuh.fit.paymentservice.presentation.response.PaymentResponse;
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
public class BookingCashCollectionInternalController {
    private final BookingCashCollectionService bookingCashCollectionService;
    private final CurrentUserProvider currentUserProvider;
    private final PaymentConverter paymentConverter;

    @PostMapping("/booking-cash-collections")
    public ResponseEntity<PaymentResponse> collect(@Valid @RequestBody CollectBookingCashRequest request) {
        var payment = bookingCashCollectionService.collect(new CollectBookingCashCommand(
                request.getPayerId(), request.getBookingId(), request.getAmount(),
                request.getCurrency(), authenticatedCollectorId()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentConverter.toResponse(payment));
    }

    private UUID authenticatedCollectorId() {
        String collectorId = currentUserProvider.getCurrentUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new InvalidPaymentException(
                        "AUTHENTICATED_COLLECTOR_REQUIRED", "Venue Service không cung cấp collector ID hợp lệ"
                ));
        try {
            return UUID.fromString(collectorId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException(
                    "AUTHENTICATED_COLLECTOR_REQUIRED", "Venue Service không cung cấp collector ID hợp lệ"
            );
        }
    }
}

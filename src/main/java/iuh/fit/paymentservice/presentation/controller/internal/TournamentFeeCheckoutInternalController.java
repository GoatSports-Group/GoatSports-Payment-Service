package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.dto.response.payment.BookingCheckoutResult;
import iuh.fit.paymentservice.application.service.TournamentFeeCheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * club-service goi khi nguoi dang ky bam "Thanh toan le phi". Nguoi tra va so tien do club-service
 * tinh tu dang ky va giai (da xac thuc nguoi dung o club-service), vi vay nhan thang trong body.
 */
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class TournamentFeeCheckoutInternalController {
    private final TournamentFeeCheckoutService tournamentFeeCheckoutService;

    @PostMapping("/tournament-fee-checkouts")
    public ResponseEntity<BookingCheckoutResult> create(@Valid @RequestBody Request request,
                                                        HttpServletRequest servletRequest) {
        BookingCheckoutResult result = tournamentFeeCheckoutService.create(new TournamentFeeCheckoutService.Command(
                request.payerId(), request.registrationId(), request.amount(), request.description(),
                "vn", clientIp(servletRequest), request.expiresAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    public record Request(
            @NotNull UUID payerId,
            @NotNull UUID registrationId,
            @NotNull @DecimalMin("1000") Double amount,
            @Size(max = 200) String description,
            @NotNull @Future LocalDateTime expiresAt
    ) { }
}

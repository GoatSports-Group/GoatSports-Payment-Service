package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.dto.request.webhook.PayOsWebhookCommand;
import iuh.fit.paymentservice.application.service.PayOsWebhookService;
import iuh.fit.paymentservice.presentation.request.PayOsWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/providers/payos")
@RequiredArgsConstructor
public class PayOsWebhookController {
    private final PayOsWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@Valid @RequestBody PayOsWebhookRequest request) {
        PayOsWebhookRequest.Data source = request.data();
        webhookService.process(new PayOsWebhookCommand(
                request.code(),
                request.desc(),
                request.success(),
                new PayOsWebhookCommand.Data(
                        source.orderCode(), source.amount(), source.description(), source.accountNumber(),
                        source.reference(), source.transactionDateTime(), source.currency(), source.paymentLinkId(),
                        source.code(), source.desc(), source.counterAccountBankId(), source.counterAccountBankName(),
                        source.counterAccountName(), source.counterAccountNumber(), source.virtualAccountName(),
                        source.virtualAccountNumber()
                ),
                request.signature()
        ));
        return ResponseEntity.noContent().build();
    }
}

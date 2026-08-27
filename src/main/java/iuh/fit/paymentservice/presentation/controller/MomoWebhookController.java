package iuh.fit.paymentservice.presentation.controller;

import iuh.fit.paymentservice.application.service.ProviderWebhookService;
import iuh.fit.paymentservice.presentation.converter.MomoWebhookConverter;
import iuh.fit.paymentservice.presentation.request.MomoWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/providers/momo/webhook")
@RequiredArgsConstructor
public class MomoWebhookController {
    private final ProviderWebhookService providerWebhookService;
    private final MomoWebhookConverter momoWebhookConverter;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@Valid @RequestBody MomoWebhookRequest request) {
        providerWebhookService.processMomoWebhook(momoWebhookConverter.toCommand(request));
        return ResponseEntity.noContent().build();
    }
}

package iuh.fit.paymentservice.application.service;

import iuh.fit.paymentservice.application.dto.request.payment.CreatePaymentCommand;
import iuh.fit.paymentservice.application.dto.response.payment.CreatePaymentResult;
import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PaymentService {
    CreatePaymentResult createPayment(CreatePaymentCommand command);

    PaymentResponseCommand getPayment(UUID paymentId, UUID payerUserId);

    List<PaymentResponseCommand> getPaymentsByReferences(
            PaymentReferenceType referenceType,
            Set<UUID> referenceIds
    );
}

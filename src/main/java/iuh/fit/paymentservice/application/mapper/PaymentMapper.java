package iuh.fit.paymentservice.application.mapper;

import iuh.fit.paymentservice.application.dto.response.payment.PaymentResponseCommand;
import iuh.fit.paymentservice.domain.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "method", ignore = true)
    @Mapping(target = "providerTransactionId", ignore = true)
    @Mapping(target = "failureCode", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    PaymentResponseCommand toPaymentResponseCommand(Payment payment);
}

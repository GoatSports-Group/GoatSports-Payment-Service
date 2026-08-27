package iuh.fit.paymentservice.application.mapper;

import iuh.fit.paymentservice.application.dto.response.PaymentResponseCommand;
import iuh.fit.paymentservice.domain.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponseCommand toPaymentResponseCommand(Payment payment);
}

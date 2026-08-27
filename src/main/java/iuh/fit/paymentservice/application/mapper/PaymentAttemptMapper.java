package iuh.fit.paymentservice.application.mapper;

import iuh.fit.paymentservice.application.dto.response.PaymentAttemptResponseCommand;
import iuh.fit.paymentservice.domain.entity.PaymentAttempt;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentAttemptMapper {
    PaymentAttemptResponseCommand toPaymentAttemptResponseCommand(PaymentAttempt paymentAttempt);
}

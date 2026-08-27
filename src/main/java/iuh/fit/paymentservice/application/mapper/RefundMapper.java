package iuh.fit.paymentservice.application.mapper;

import iuh.fit.paymentservice.application.dto.response.refund.RefundResponseCommand;
import iuh.fit.paymentservice.domain.entity.Refund;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefundMapper {
    RefundResponseCommand toRefundResponseCommand(Refund refund);
}

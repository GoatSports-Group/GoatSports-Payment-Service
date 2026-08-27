package iuh.fit.paymentservice.presentation.converter;

import iuh.fit.paymentservice.application.dto.request.MomoWebhookCommand;
import iuh.fit.paymentservice.presentation.request.MomoWebhookRequest;
import org.springframework.stereotype.Component;

@Component
public class MomoWebhookConverter {
    public MomoWebhookCommand toCommand(MomoWebhookRequest request) {
        return MomoWebhookCommand.builder()
                .partnerCode(request.getPartnerCode())
                .orderId(request.getOrderId())
                .requestId(request.getRequestId())
                .amount(request.getAmount())
                .orderInfo(request.getOrderInfo())
                .orderType(request.getOrderType())
                .transId(request.getTransId())
                .resultCode(request.getResultCode())
                .message(request.getMessage())
                .payType(request.getPayType())
                .responseTime(request.getResponseTime())
                .extraData(request.getExtraData())
                .signature(request.getSignature())
                .build();
    }
}

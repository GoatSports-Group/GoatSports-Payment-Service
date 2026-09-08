package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteManualRefundRequest {
    @NotBlank(message = "Mã giao dịch hoàn tiền không được để trống.")
    @Size(max = 100, message = "Mã giao dịch hoàn tiền không được vượt quá 100 ký tự.")
    private String providerRefundId;
}

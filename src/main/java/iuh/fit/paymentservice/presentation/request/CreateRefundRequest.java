package iuh.fit.paymentservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRefundRequest {
    @NotBlank(message = "Lý do refund là bắt buộc")
    @Size(max = 500, message = "Lý do refund không được vượt quá 500 ký tự")
    private String reason;
    @NotNull(message = "Số tiền refund là bắt buộc")
    @Positive(message = "Số tiền refund phải lớn hơn 0")
    private Double amount;
    @NotNull(message = "Phải xác định chế độ tự động giải ngân")
    private Boolean autoDisburse;
}

package iuh.fit.paymentservice.presentation.request;

import iuh.fit.paymentservice.domain.enums.PaymentReferenceType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class PaymentReferenceQueryRequest {
    @NotNull(message = "Reference type là bắt buộc")
    private PaymentReferenceType referenceType;

    @NotEmpty(message = "Phải có ít nhất một reference ID")
    @Size(max = 100, message = "Mỗi truy vấn hỗ trợ tối đa 100 reference ID")
    private Set<UUID> referenceIds;
}

package iuh.fit.paymentservice.application.service.impl;

import iuh.fit.paymentservice.application.port.persistence.PaymentRepository;
import iuh.fit.paymentservice.application.service.PaymentCreationTransactionService;
import iuh.fit.paymentservice.domain.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCreationTransactionServiceImpl implements PaymentCreationTransactionService {
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment create(Payment payment) {
        return paymentRepository.save(payment);
    }
}

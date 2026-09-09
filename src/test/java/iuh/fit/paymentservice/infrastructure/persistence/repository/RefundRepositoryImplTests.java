package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.domain.entity.Refund;
import iuh.fit.paymentservice.infrastructure.persistence.entity.RefundEntity;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.RefundEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaRefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundRepositoryImplTests {
    @Mock
    private JpaRefundRepository jpaRefundRepository;
    @Mock
    private RefundEntityMapper refundEntityMapper;
    @InjectMocks
    private RefundRepositoryImpl refundRepository;

    @Test
    void updatesManagedEntityWithoutReplacingImmutablePaymentRelationship() {
        UUID refundId = UUID.randomUUID();
        Refund refund = Refund.builder().refundId(refundId).build();
        RefundEntity managedEntity = RefundEntity.builder().refundId(refundId).build();

        when(jpaRefundRepository.findById(refundId)).thenReturn(Optional.of(managedEntity));
        when(jpaRefundRepository.saveAndFlush(managedEntity)).thenReturn(managedEntity);
        when(refundEntityMapper.toDomain(managedEntity)).thenReturn(refund);

        Refund saved = refundRepository.save(refund);

        assertThat(saved).isSameAs(refund);
        verify(refundEntityMapper).updateEntity(refund, managedEntity);
        verify(refundEntityMapper, never()).toEntity(refund);
        verify(jpaRefundRepository).saveAndFlush(managedEntity);
    }
}

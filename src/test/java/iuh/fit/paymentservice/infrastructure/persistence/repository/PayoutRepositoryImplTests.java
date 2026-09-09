package iuh.fit.paymentservice.infrastructure.persistence.repository;

import iuh.fit.paymentservice.domain.entity.Payout;
import iuh.fit.paymentservice.infrastructure.persistence.entity.PayoutEntity;
import iuh.fit.paymentservice.infrastructure.persistence.mapper.PayoutEntityMapper;
import iuh.fit.paymentservice.infrastructure.persistence.repository.jpa.JpaPayoutRepository;
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
class PayoutRepositoryImplTests {
    @Mock
    private JpaPayoutRepository jpaRepository;
    @Mock
    private PayoutEntityMapper mapper;
    @InjectMocks
    private PayoutRepositoryImpl payoutRepository;

    @Test
    void updatesManagedEntityWithoutReplacingImmutableRelationships() {
        UUID payoutId = UUID.randomUUID();
        Payout payout = Payout.builder().payoutId(payoutId).build();
        PayoutEntity managedEntity = PayoutEntity.builder().payoutId(payoutId).build();

        when(jpaRepository.findById(payoutId)).thenReturn(Optional.of(managedEntity));
        when(jpaRepository.saveAndFlush(managedEntity)).thenReturn(managedEntity);
        when(mapper.toDomain(managedEntity)).thenReturn(payout);

        Payout saved = payoutRepository.save(payout);

        assertThat(saved).isSameAs(payout);
        verify(mapper).updateEntity(payout, managedEntity);
        verify(mapper, never()).toEntity(payout);
        verify(jpaRepository).saveAndFlush(managedEntity);
    }
}

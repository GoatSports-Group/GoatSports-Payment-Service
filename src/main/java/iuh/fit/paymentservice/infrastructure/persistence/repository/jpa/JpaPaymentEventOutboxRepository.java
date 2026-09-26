package iuh.fit.paymentservice.infrastructure.persistence.repository.jpa;

import iuh.fit.paymentservice.infrastructure.persistence.entity.PaymentEventOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaPaymentEventOutboxRepository extends JpaRepository<PaymentEventOutboxEntity, UUID> {

    /** Khóa các sự kiện chưa gửi theo thứ tự tạo; SKIP LOCKED để nhiều instance không gửi trùng một dòng. */
    @Query(value = "select * from payment_event_outbox where published_at is null "
            + "order by created_at limit :limit for update skip locked", nativeQuery = true)
    List<PaymentEventOutboxEntity> lockPending(int limit);
}

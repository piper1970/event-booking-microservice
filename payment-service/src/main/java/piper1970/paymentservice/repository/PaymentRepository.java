package piper1970.paymentservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.paymentservice.domain.Payment;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
}

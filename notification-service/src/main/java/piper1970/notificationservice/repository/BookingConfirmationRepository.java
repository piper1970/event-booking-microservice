package piper1970.notificationservice.repository;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.notificationservice.domain.BookingConfirmation;
import reactor.core.publisher.Mono;

@Repository
public interface BookingConfirmationRepository extends
    ReactiveCrudRepository<BookingConfirmation, Integer> {

  Mono<BookingConfirmation> findByConfirmationString(UUID confirmationString);
}

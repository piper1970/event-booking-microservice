package piper1970.notificationservice.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BookingConfirmationRepository extends
    ReactiveCrudRepository<BookingConfirmation, Integer> {

  Mono<BookingConfirmation> findByConfirmationString(UUID confirmationString);

  Flux<BookingConfirmation> findByConfirmationStatus(ConfirmationStatus confirmationStatus);

  Mono<Integer> deleteByConfirmationDateTimeBefore(LocalDateTime deletionDateTime);

}

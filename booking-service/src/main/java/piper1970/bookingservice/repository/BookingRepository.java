package piper1970.bookingservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.bookingservice.domain.Booking;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BookingRepository extends ReactiveCrudRepository<Booking, Integer> {
  Flux<Booking> findByUsername(String username);
  Mono<Booking> findByIdAndUsername(Integer id, String username);
}

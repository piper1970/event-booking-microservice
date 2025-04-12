package piper1970.bookingservice.repository;

import java.util.Collection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BookingRepository extends ReactiveCrudRepository<Booking, Integer> {
  Flux<Booking> findByUsername(String username);
  Mono<Booking> findByIdAndUsername(Integer id, String username);
  Flux<BookingSummary> findByEventIdAndBookingStatusNotIn(Integer eventId, Collection<BookingStatus> statuses);

  Flux<Booking> findBookingsByEventIdAndBookingStatusIn(Integer eventId, Collection<BookingStatus> statuses);

}

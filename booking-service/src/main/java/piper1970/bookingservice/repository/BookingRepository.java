package piper1970.bookingservice.repository;

import java.util.Collection;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
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

  // TODO: need to test/verify this SQL update query
  @Modifying
  @Query("UPDATE event_service.bookings SET booking_status = 'COMPLETED' WHERE event_id = :eventId AND booking_status = 'CONFIRMED'")
  Mono<Integer> completeConfirmedBookingsForEvent(Integer eventId);

//  @Modifying
//  @Query("UPDATE Booking b SET b.bookingStatus ='COMPLETED' WHERE b.eventId =:eventId AND b.bookingStatus = 'CONFIRMED'")
//  Mono<Integer> completeConfirmedBookingsForEvent2(Integer eventId);
}

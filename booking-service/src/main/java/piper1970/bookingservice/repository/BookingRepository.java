package piper1970.bookingservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.bookingservice.domain.Booking;

@Repository
public interface BookingRepository extends ReactiveCrudRepository<Booking, Integer> {
}

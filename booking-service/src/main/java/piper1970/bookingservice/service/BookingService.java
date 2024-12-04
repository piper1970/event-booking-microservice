package piper1970.bookingservice.service;

import piper1970.bookingservice.domain.Booking;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BookingService {
  Flux<Booking> findAllBookings();
  Mono<Booking> findBookingById(Integer id);
}

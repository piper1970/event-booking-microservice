package piper1970.bookingservice.service;

import piper1970.bookingservice.domain.Booking;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BookingService {
  Flux<Booking> findAllBookings();
  Mono<Booking> findBookingById(Integer id);
  Mono<Booking> createBooking(Booking booking);
  Mono<Booking> updateBooking(Booking booking);
  Mono<Void> deleteBooking(Integer id);
}

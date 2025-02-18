package piper1970.bookingservice.service;

import piper1970.bookingservice.domain.Booking;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BookingWebService {
  Flux<Booking> findAllBookings();
  Flux<Booking> findBookingsByUsername(String username);
  Mono<Booking> findBookingById(Integer id);
  Mono<Booking> findBookingIdByIdAndUsername(Integer id, String username);
  Mono<Booking> createBooking(Booking booking, String token);
  Mono<Booking> updateBooking(Booking booking);
  Mono<Void> cancelBooking(Integer id);
}

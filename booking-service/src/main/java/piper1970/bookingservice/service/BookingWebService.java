package piper1970.bookingservice.service;

import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BookingWebService {
  Flux<BookingDto> findAllBookings();
  Flux<BookingDto> findBookingsByUsername(String username);
  Mono<BookingDto> findBookingById(Integer id);
  Mono<BookingDto> findBookingByIdAndUsername(Integer id, String username);
  Mono<BookingDto> createBooking(BookingCreateRequest booking, String token);
  Mono<Void> deleteBooking(Integer id, String token);
  Mono<BookingDto> cancelBooking(Integer id, String token);
  Mono<BookingDto> cancelBooking(Integer id, String username, String token);

}

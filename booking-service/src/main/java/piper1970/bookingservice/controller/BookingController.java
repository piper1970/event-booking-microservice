package piper1970.bookingservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.service.BookingService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;

  @GetMapping
  public Flux<Booking> getAllBookings() {
    return bookingService.findAllBookings();
  }

  @GetMapping("{id}")
  public Mono<Booking> getBookingById(@PathVariable("id") Integer id) {
    return bookingService.findBookingById(id);
  }

}

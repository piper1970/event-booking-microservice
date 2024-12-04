package piper1970.bookingservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.bookingservice.dto.mapper.BookingMapper;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.service.BookingService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;
  private final BookingMapper bookingMapper;

  @GetMapping
  public Flux<BookingDto> getAllBookings() {
    return bookingService.findAllBookings()
        .map(bookingMapper::toDto);
  }

  @GetMapping("{id}")
  public Mono<BookingDto> getBookingById(@PathVariable("id") Integer id) {
    return bookingService.findBookingById(id)
        .map(bookingMapper::toDto);
  }

}

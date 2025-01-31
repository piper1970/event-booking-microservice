package piper1970.bookingservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.bookingservice.dto.mapper.BookingMapper;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
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
//  @PreAuthorize("hasRole('MEMBER')")
  public Flux<BookingDto> getAllBookings(OidcUserAuthority userAuthority) {
    return bookingService.findAllBookings()
        .map(bookingMapper::toDto);
  }

  @GetMapping("{id}")
  @PreAuthorize("hasRole('MEMBER')")
  public Mono<BookingDto> getBookingById(@PathVariable Integer id) {
    return bookingService.findBookingById(id)
        .map(bookingMapper::toDto)
        .switchIfEmpty(Mono.error(new BookingNotFoundException("Booking not found for id: " + id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('MEMBER')")
  public Mono<BookingDto> createBooking(@Valid @RequestBody BookingDto bookingDto) {
    return bookingService.createBooking(bookingMapper.toEntity(bookingDto))
        .map(bookingMapper::toDto);
  }

  @PutMapping("{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Mono<BookingDto> updateBooking(@PathVariable Integer id,
      @Valid @RequestBody BookingDto bookingDto) {
    return bookingService.updateBooking(bookingMapper.toEntity(bookingDto).withId(id))
        .map(bookingMapper::toDto);
  }

  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public Mono<Void> deleteBooking(@PathVariable Integer id) {
    return bookingService.deleteBooking(id);
  }
}

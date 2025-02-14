package piper1970.bookingservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.bookingservice.domain.BookingStatus;
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
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<BookingDto> getAllBookings(@AuthenticationPrincipal JwtAuthenticationToken token) {

    var isAdmin = determineIfAdmin(token);

    if (isAdmin) {
      return bookingService.findAllBookings()
          .map(bookingMapper::toDto);
    }

    var creds = (Jwt)token.getCredentials();
    return bookingService.findBookingsByUsername(getUserFromToken(creds))
        .map(bookingMapper::toDto);
  }

  @GetMapping("{id}")
  @PreAuthorize("hasAuthority('MEMBER')") // need to ensure proper user
  public Mono<BookingDto> getBookingById(@AuthenticationPrincipal JwtAuthenticationToken token,
      @PathVariable Integer id) {

    var isAdmin = determineIfAdmin(token);

    if (isAdmin) {
      return bookingService.findBookingById(id)
          .map(bookingMapper::toDto)
          .switchIfEmpty(Mono.error(new BookingNotFoundException("Booking not found for id: " + id)));
    }

    var creds = (Jwt)token.getCredentials();
    return bookingService.findBookingIdByIdAndUsername(id, getUserFromToken(creds))
        .map(bookingMapper::toDto)
        .switchIfEmpty(Mono.error(new BookingNotFoundException("Booking not found for id: " + id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<BookingDto> createBooking(@AuthenticationPrincipal JwtAuthenticationToken token,
      @Valid @RequestBody BookingDto bookingDto) {

    // ensure username in dto is set to current user
    var creds = (Jwt)token.getCredentials();
    var username = getUserFromToken(creds);

    // ensure bookingStatus is at beginning (IN_PROGRESS) and username is current user
    var entity = bookingMapper.toEntity(bookingDto);
    entity.setUsername(username);
    entity.setBookingStatus(BookingStatus.IN_PROGRESS);

    return bookingService.createBooking(entity)
        .map(bookingMapper::toDto);
  }

  @PutMapping("{id}")
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<BookingDto> updateBooking(@PathVariable Integer id,
      @Valid @RequestBody BookingDto bookingDto) {
    return bookingService.updateBooking(bookingMapper.toEntity(bookingDto).withId(id))
        .map(bookingMapper::toDto);
  }

  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<Void> deleteBooking(@PathVariable Integer id) {
    return bookingService.deleteBooking(id);
  }

  // TODO: move these helper functions into a utility in commons
  // helper to extract username from JWT token
  private String getUserFromToken(Jwt token){
    return token.getClaimAsString("preferred_username");
  }

  // helper to extract requested authority from token
  private boolean determineIfAdmin(JwtAuthenticationToken token){
    return token.getAuthorities().stream()
        .anyMatch(auth -> "ADMIN".equals(auth.getAuthority()));
  }
}

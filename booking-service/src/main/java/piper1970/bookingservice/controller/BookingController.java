package piper1970.bookingservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import piper1970.bookingservice.dto.mapper.BookingMapper;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.dto.model.BookingUpdateRequest;
import piper1970.bookingservice.service.BookingWebService;
import piper1970.eventservice.common.exceptions.BookingNotFoundException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.tokens.TokenUtilities;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

  private final BookingWebService bookingWebService;
  private final BookingMapper bookingMapper;

  @GetMapping
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<BookingDto> getAllBookings(@AuthenticationPrincipal JwtAuthenticationToken token) {

    var username = TokenUtilities.getUserFromToken(token);
    log.debug("Getting all bookings called by [{}]", username);

    if (TokenUtilities.isAdmin(token)) {
      return bookingWebService.findAllBookings()
          .map(bookingMapper::entityToDto);
    }

    return bookingWebService.findBookingsByUsername(username)
        .map(bookingMapper::entityToDto);
  }

  @GetMapping("{id}")
  @PreAuthorize("hasAuthority('MEMBER')") // need to ensure proper user
  public Mono<BookingDto> getBookingById(@AuthenticationPrincipal JwtAuthenticationToken token,
      @PathVariable Integer id) {

    var username = TokenUtilities.getUserFromToken(token);
    log.debug("Getting booking[{}] called by [{}]", id, username);

    if (TokenUtilities.isAdmin(token)) {
      return bookingWebService.findBookingById(id)
          .map(bookingMapper::entityToDto)
          .switchIfEmpty(Mono.error(new BookingNotFoundException("Booking not found for id: " + id)));
    }

    return bookingWebService.findBookingIdByIdAndUsername(id, username)
        .map(bookingMapper::entityToDto)
        .switchIfEmpty(Mono.error(new BookingNotFoundException("Booking not found for id: " + id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<BookingDto> createBooking(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @Valid @RequestBody BookingCreateRequest createRequest) {

    var user = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Creating booking called by [{}]", user);

    createRequest.setUsername(user);

    var token = jwtToken.getToken().getTokenValue(); // needed for event-service call

    return bookingWebService.createBooking(createRequest, token)
        .map(bookingMapper::entityToDto)
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not available for id: " + createRequest.getEventId())));
  }

  @PutMapping("{id}")
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<BookingDto> updateBooking(
      @AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id,
      @Valid @RequestBody BookingUpdateRequest updateRequest) {

    var user = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Updating booking[{}] called by [{}]", id, user);

    return bookingWebService.updateBooking(id, updateRequest)
        .map(bookingMapper::entityToDto);
  }

  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<Void> deleteBooking(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id) {
    var user = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Deleting booking[{}] called by [{}]", id, user);

    return bookingWebService.deleteBooking(id);
  }

}

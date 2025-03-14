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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.service.BookingWebService;
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

  @GetMapping
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<BookingDto> getAllBookings(@AuthenticationPrincipal JwtAuthenticationToken token) {

    var username = TokenUtilities.getUserFromToken(token);

    log.debug("Getting all bookings called by [{}]", username);

    if (TokenUtilities.isAdmin(token)) {
      return bookingWebService.findAllBookings();
    }

    return bookingWebService.findBookingsByUsername(username);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('MEMBER')") // need to ensure proper user
  public Mono<BookingDto> getBookingById(@AuthenticationPrincipal JwtAuthenticationToken token,
      @PathVariable Integer id) {

    var username = TokenUtilities.getUserFromToken(token);

    log.debug("Getting booking[{}] called by [{}]", id, username);

    if (TokenUtilities.isAdmin(token)) {
      return bookingWebService.findBookingById(id)
          .switchIfEmpty(Mono.error(new BookingNotFoundException(createBookingNotFoundMessage(id))));
    }

    return bookingWebService.findBookingByIdAndUsername(id, username)
        .switchIfEmpty(Mono.error(new BookingNotFoundException(createBookingNotFoundMessage(id))));
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
        .switchIfEmpty(Mono.error(new EventNotFoundException(createEventNotFountMessage(createRequest.getEventId()))));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<Void> delete(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id) {

    var user = TokenUtilities.getUserFromToken(jwtToken);

    log.debug("Delete booking[{}] called by [{}]", id, user);

    var token = jwtToken.getToken().getTokenValue();

    return bookingWebService.deleteBooking(id, token);
  }

  // TODO: needs testing
  @PatchMapping("/{id}/cancel")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<BookingDto> cancel(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id) {
    var user = TokenUtilities.getUserFromToken(jwtToken);

    log.debug("Cancel booking[{}] called by [{}]", id, user);

    var token = jwtToken.getToken().getTokenValue();

    if(TokenUtilities.isAdmin(jwtToken)) {
      return bookingWebService.cancelBooking(id, token);
    }else{
      return bookingWebService.cancelBooking(id, token, user);
    }
  }

  private String createBookingNotFoundMessage(Integer eventId) {
    return String.format("Booking [%d] not found", eventId);
  }

  private String createEventNotFountMessage(Integer eventId) {
    return String.format("Event [%d] not found", eventId);
  }
}

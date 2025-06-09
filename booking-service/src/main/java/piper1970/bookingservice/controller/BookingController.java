package piper1970.bookingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import piper1970.bookingservice.service.BookingWebService;
import piper1970.eventservice.common.tokens.TokenUtilities;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Booking Controller")
public class BookingController {

  private final BookingWebService bookingWebService;

  @Operation(
      summary = "Get all personal bookings for events that you've made",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "found all personal bookings",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      array = @ArraySchema(schema = @Schema(implementation = BookingDto.class))
                  )
              }),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @GetMapping
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<BookingDto> getAllBookings(@AuthenticationPrincipal JwtAuthenticationToken token) {

    var username = TokenUtilities.getUserFromToken(token);

    log.debug("Getting all bookings called by [{}]", username);

    return bookingWebService.findBookingsByUsername(username);
  }

  @Operation(
      summary = "Get personal booking for event by id",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "found personal booking",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = BookingDto.class)
                  )
              }),
          @ApiResponse(
              responseCode = "404",
              description = "personal booking not found for given id",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('MEMBER')") // need to ensure proper user
  public Mono<BookingDto> getBookingById(@AuthenticationPrincipal JwtAuthenticationToken token,
      @Parameter(description = "id of booking to retrieve") @PathVariable Integer id) {

    var username = TokenUtilities.getUserFromToken(token);

    log.debug("Getting booking[{}] called by [{}]", id, username);

    return bookingWebService.findBookingByIdAndUsername(id, username);
  }

  @Operation(
      summary = "Create new booking for given event id",
      responses = {
          @ApiResponse(
              responseCode = "201",
              description = "booking created for event",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = BookingDto.class)
                  )
              }),
          @ApiResponse(
              responseCode = "404",
              description = "event not found for given eventId field",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "422",
              description = "Attempt to create booking for event already in progress not allowed",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<BookingDto> createBooking(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "json create-booking request",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = BookingCreateRequest.class),
              examples = @ExampleObject(value = """
                  {"eventId": 27}
                  """
              )
          )
      ) @Valid @RequestBody BookingCreateRequest createRequest) {

    var user = TokenUtilities.getUserFromToken(jwtToken);
    var email = TokenUtilities.getEmailFromToken(jwtToken);

    // needed for service call to event-service
    var token = jwtToken.getToken().getTokenValue();

    log.debug("Creating booking called by [{}]", user);

    // set username/email based on caller credentials
    createRequest.setUsername(user);
    createRequest.setEmail(email);

    return bookingWebService.createBooking(createRequest, token);
  }

  @Operation(
      summary = "cancel personal booking to event",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "booking has been successfully cancelled",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = BookingDto.class)
                  )
              }
          ),
          @ApiResponse(
              responseCode = "404",
              description = "booking not found for given id",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "409",
              description = "cannot cancel booking because event has already started",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @PatchMapping("/{id}/cancel")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<BookingDto> cancel(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @Parameter(description = "id of booking to cancel") @PathVariable Integer id) {

    var user = TokenUtilities.getUserFromToken(jwtToken);

    // needed for service call to event-service
    var token = jwtToken.getToken().getTokenValue();

    log.debug("Cancel booking[{}] called by [{}]", id, user);

    return bookingWebService.cancelBooking(id, user, token);
  }

}

package piper1970.notificationservice.routehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.exceptions.ConfirmationNotFoundException;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class BookingConfirmationHandler {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MessagePostingService messagePostingService;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration notificationTimeoutDuration;

  //region Main Handler

  public Mono<ServerResponse> handleConfirmation(ServerRequest request) {

    var confirmationString = request.pathVariable("confirmationString");
    log.debug("HandleConfirmation request received with token [{}]", confirmationString);

    try {
      var confimationUUID = UUID.fromString(confirmationString);

      return bookingConfirmationRepository.findByConfirmationString(confimationUUID)
          .timeout(notificationTimeoutDuration)
          // avoid double-clicking on confirm token
          .filter(confirmation -> confirmation.getConfirmationStatus() == ConfirmationStatus.AWAITING_CONFIRMATION)
          .switchIfEmpty(Mono.fromCallable(() -> {
            var message = "Booking confirmation string [%s] not found".formatted(
                confirmationString);
            log.warn(message);
            throw new ConfirmationNotFoundException(message);
          }))
          .flatMap(confirmation -> handleConfirmationLogic(confirmation, confirmationString))
          .onErrorResume(ConfirmationNotFoundException.class, e ->
              buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), pd -> {
                pd.setTitle("Booking confirmation not found");
                pd.setType(URI.create("http://notification-service/booking-confirmation-not-found"));
              }).map(body ->
                      ServerResponse.status(HttpStatus.NOT_FOUND)
                          .contentType(MediaType.APPLICATION_JSON)
                          .bodyValue(body))
                  .orElseGet(() -> ServerResponse.notFound().build()))
          .onErrorResume(TimeoutException.class, e -> {
            log.warn("Repository timed out during find of confirmation string {}: {}",
                confirmationString, e.getMessage(),
                e);
            return handleServiceUnavailableResponse();
          });

    } catch (IllegalArgumentException e) {
      var message = "[%s] is not a UUID-formatted string".formatted(confirmationString);
      log.warn(message, e);
      return buildErrorResponse(HttpStatus.BAD_REQUEST, message, pd -> {
        pd.setTitle("Malformed confirmation-id");
        pd.setType(URI.create("http://notification-service/malformed-confirmation-id"));
      })
          .map(body ->
              ServerResponse.status(HttpStatus.BAD_REQUEST)
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(body))
          .orElseGet(() -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }
  }

  //endregion Main Handler

  //region Helper Methods

  //region Avro Message Builder

  private BookingConfirmed buildBookingConfirmedMessage(BookingConfirmation confirmation) {
    var bookingId = new BookingId();
    bookingId.setId(confirmation.getBookingId());
    bookingId.setEmail(confirmation.getBookingEmail());
    bookingId.setUsername(confirmation.getBookingUser());
    return new BookingConfirmed(bookingId, confirmation.getEventId());
  }

  private BookingExpired buildBookingExpiredMessage(BookingConfirmation confirmation) {
    var bookingId = new BookingId();
    bookingId.setId(confirmation.getBookingId());
    bookingId.setEmail(confirmation.getBookingEmail());
    bookingId.setUsername(confirmation.getBookingUser());
    return new BookingExpired(bookingId, confirmation.getEventId());
  }

  //endregion Avro Message Builder

  //region JSON marshalling logic

  private Optional<String> buildBookingConfirmedJson(BookingConfirmation confirmation) {
    var template = "Booking [%d] successfully confirmed at [%s] for for event [%d]";
    var message = String.format(template, confirmation.getBookingId(),
        confirmation.getConfirmationDateTime(),
        confirmation.getEventId());
    var props = Map.of("status", "success", "message", message);

    try {
      return Optional.ofNullable(
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(props));
    } catch (Exception ex) {
      log.error("Unable to marshal to json", ex);
      return Optional.empty();
    }
  }

  /// Build rfc9457-compliant json error response
  private Optional<String> buildErrorResponse(HttpStatus status, String message,
      Consumer<ProblemDetail> handler) {
    var problem = ProblemDetail.forStatusAndDetail(status, message);
    handler.accept(problem);

    try {
      return Optional.ofNullable(
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(problem));
    } catch (JsonProcessingException ex) {
      log.error("Unable to marshal to json", ex);
      return Optional.empty();
    }
  }

  //endregion JSON marshalling logic

  //region Confirmation Logic

  /// Logic to handle both confirmation success or failure due to expiration behavior
  private Mono<ServerResponse> handleConfirmationLogic(BookingConfirmation confirmation,
      String confirmationString) {

    if (confirmBooking(confirmation)) {

      var updatedConfirmation = confirmation.toBuilder()
          .confirmationStatus(ConfirmationStatus.CONFIRMED)
          .build();

      return bookingConfirmationRepository.save(updatedConfirmation)
          .timeout(notificationTimeoutDuration)
          .doOnNext(bookingConfirmation -> {
            // post to confirmation to kafka channel
            log.debug("Booking confirmation [{}] successfully saved. Relaying success to BOOKING_CONFIRMED topic", bookingConfirmation);
            var message = buildBookingConfirmedMessage(bookingConfirmation);
            messagePostingService.postBookingConfirmedMessage(message);
          })
          .flatMap(savedConfirmation ->
              buildBookingConfirmedJson(confirmation)
                  .map(json ->
                      ServerResponse.ok()
                          .contentType(MediaType.APPLICATION_JSON)
                          .bodyValue(json))
                  .orElseGet(() ->
                      // fallback if json marshalling fails
                      ServerResponse.ok()
                          .contentType(MediaType.TEXT_PLAIN)
                          .bodyValue(
                              "Booking has been confirmed for event " + confirmation.getEventId())
                  ))
      .onErrorResume(TimeoutException.class, e -> {
        log.error("Repository timed out during save of confirmed booking confirmation: [{}]. Manual adjustment may be necessary", e.getMessage(),
            e);
        return handleServiceUnavailableResponse();
      });
    } else {

      var expiredConfirmation = confirmation.toBuilder()
          .confirmationStatus(ConfirmationStatus.EXPIRED)
          .build();

      var errorMessage = "Booking confirmation string [%s] has expired".formatted(
          confirmationString);

      log.warn(errorMessage);

      return bookingConfirmationRepository.save(expiredConfirmation)
          .timeout(notificationTimeoutDuration)
          .doOnNext(bookingConfirmation -> {
            log.debug("Expired booking saved [{}]. Relaying failure to BOOKING_EXPIRED topic", bookingConfirmation);
            var message = buildBookingExpiredMessage(bookingConfirmation);
            messagePostingService.postBookingExpiredMessage(message);
          })
          .flatMap(_ignored ->
              buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage, pd -> {
                pd.setTitle("Booking confirmation expired");
                pd.setType(URI.create("http://notification-service/booking-confirmation-expired"));
              })
                  .map(msg -> ServerResponse.badRequest()
                      .contentType(MediaType.APPLICATION_JSON)
                      .bodyValue(msg)
                  ).orElseGet(() -> ServerResponse.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("Booking has been expired for event " + confirmation.getEventId())
                  )
          ).onErrorResume(TimeoutException.class, e -> {
            log.error("Repository timed out during save of expired booking: [{}]. Manual adjustment may be necessary", e.getMessage(),
                e);
            return handleServiceUnavailableResponse();
          });
    }
  }

  private boolean confirmBooking(BookingConfirmation confirmation) {
    var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    var expirationTime = confirmation.getConfirmationDateTime()
        .plusMinutes(confirmation.getDurationInMinutes())
        .truncatedTo(ChronoUnit.MINUTES);
    return now.isBefore(expirationTime);
  }

  //endregion Confirmation Logic

  private Mono<ServerResponse> handleServiceUnavailableResponse(){
    var message = "Service Unavailable. Please try again later.";
    return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, message, pd -> {
      pd.setTitle("Service Unavailable");
      pd.setType(URI.create("http://notification-service/service-unavailable"));
    })
        .map(body -> ServerResponse
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body))
        .orElseGet(() -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(message));
  }

  //endregion Helper Methods

}

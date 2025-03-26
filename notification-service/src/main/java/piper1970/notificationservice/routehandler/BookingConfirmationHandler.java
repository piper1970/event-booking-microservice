package piper1970.notificationservice.routehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.exceptions.ConfirmationNotFoundException;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationHandler {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MessagePostingService messagePostingService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  //region Main Handler

  public Mono<ServerResponse> handleConfirmation(ServerRequest request) {

    var confirmationString = request.pathVariable("confirmationString");

    try {
      var confimationUUID = UUID.fromString(confirmationString);

      // TODO: need to handle timeout behavior
      return bookingConfirmationRepository.findByConfirmationString(confimationUUID)
          .switchIfEmpty(Mono.defer(() -> {
            var message = "Booking confirmation string [%s] not found".formatted(
                confirmationString);
            log.warn(message);
            return Mono.error(new ConfirmationNotFoundException(message));
          }))
          .flatMap(confirmation -> handleConfirmationLogic(confirmation, confirmationString))
          .onErrorResume(ConfirmationNotFoundException.class, e ->
              buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), pd -> {
                pd.setTitle("Booking confirmation failed");
                pd.setType(URI.create("http://notification-service/booking-confirmation-failed"));
              }).map(body ->
                      ServerResponse.status(HttpStatus.NOT_FOUND)
                          .contentType(MediaType.APPLICATION_JSON)
                          .body(BodyInserters.fromValue(body)))
                  .orElseGet(() -> ServerResponse.notFound().build()));

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
                  .body(BodyInserters.fromValue(body)))
          .orElseGet(() -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }
  }

  //endregion Main Handler

  //region Helper Methods

  //region Avro Message Builder

  private BookingConfirmed buildBookingConfirmedMessage(BookingConfirmation confirmation) {
    var message = new BookingConfirmed();
    message.setEventId(confirmation.getEventId());
    var bookingId = new BookingId();
    bookingId.setId(confirmation.getBookingId());
    bookingId.setEmail(confirmation.getBookingEmail());
    bookingId.setUsername(confirmation.getBookingUser());
    message.setBooking(bookingId);
    return message;
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
          .doOnNext(bookingConfirmation -> {
            // post to confirmation to kafka channel
            var message = buildBookingConfirmedMessage(bookingConfirmation);
            messagePostingService.postBookingConfirmedMessage(message);
          })
          .flatMap(savedConfirmation ->
              buildBookingConfirmedJson(confirmation)
                  .map(json ->
                      ServerResponse.ok()
                          .contentType(MediaType.APPLICATION_JSON)
                          .body(BodyInserters.fromValue(json)))
                  .orElseGet(() ->
                      // fallback if json marshalling fails
                      ServerResponse.ok()
                          .contentType(MediaType.TEXT_PLAIN)
                          .body(BodyInserters.fromValue(
                              "Booking has been confirmed for event " + confirmation.getEventId()))
                  ));
    } else {

      var expiredConfirmation = confirmation.toBuilder()
          .confirmationStatus(ConfirmationStatus.EXPIRED)
          .build();

      var errorMessage = "Booking confirmation string [%s] has expired".formatted(
          confirmationString);

      log.warn(errorMessage);

      return bookingConfirmationRepository.save(expiredConfirmation)
          .flatMap(_ignored ->
              buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage, pd -> {
                pd.setTitle("Booking confirmation expired");
                pd.setType(URI.create("http://notification-service/booking-confirmation-expired"));
              })
                  .map(msg -> ServerResponse.badRequest()
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(BodyInserters.fromValue(msg))
                  ).orElseGet(() -> {
                    // fallback if JSON marshalling fails
                    return ServerResponse.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(BodyInserters.fromValue(
                            "Booking has been expired for event " + confirmation.getEventId()));
                  })
          );
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

  //endregion Helper Methods

}

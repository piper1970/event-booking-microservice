package piper1970.notificationservice.routehandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.exceptions.ConfirmationExpiredException;
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
          // handles both confirmation success and failure due to expiration
          .flatMap(confirmation -> {
            if(confirmBooking(confirmation)) {
              var updatedConfirmation = confirmation.toBuilder()
                  .confirmationStatus(ConfirmationStatus.CONFIRMED)
                  .build();
              // save confirmed confirmation to repository
              return bookingConfirmationRepository.save(updatedConfirmation)
                  // post to kafka
                  .doOnNext(bookingConfirmation -> {
                      var message = buildBookingConfirmedMessage(bookingConfirmation);
                      messagePostingService.postBookingConfirmedMessage(message);
                  })
                  // return result OK with json response
                  .flatMap(savedConfirmation -> {
                    var json = buildBookingConfirmedJson(confirmation);
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(json));
                  });
            }else{
              var updatedConfirmation = confirmation.toBuilder()
                  .confirmationStatus(ConfirmationStatus.EXPIRED)
                  .build();
              // save expired confirmation
              return bookingConfirmationRepository.save(updatedConfirmation)
                  // throw deferred error for later consumption
                  .then(Mono.defer(() -> {
                    var message = "Booking confirmation string [%s] has expired".formatted(
                        confirmationString);
                    log.warn(message);
                    return Mono.error(new ConfirmationExpiredException(message));
                  }));
            }
          })
          .onErrorResume(ConfirmationNotFoundException.class, e ->
              ServerResponse.status(HttpStatus.NOT_FOUND)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(
                      BodyInserters.fromValue(buildErrorJson(HttpStatus.NOT_FOUND, e.getMessage())))
          ).onErrorResume(ConfirmationExpiredException.class, e ->
              ServerResponse.badRequest()
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(BodyInserters.fromValue(
                      buildErrorJson(HttpStatus.BAD_REQUEST, e.getMessage())))
          );
    } catch (IllegalArgumentException e) {
      // in case param string does not conform to uuid spec.
      var message = "[%s] is not a UUID-formatted string".formatted(confirmationString);
      log.warn(message, e);
      return ServerResponse
          .badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(BodyInserters.fromValue(buildErrorJson(HttpStatus.BAD_REQUEST, message)));
    }
  }

  private BookingConfirmed buildBookingConfirmedMessage(BookingConfirmation confirmation) {
    var message = new BookingConfirmed();
    message.setEventId(confirmation.getEventId());
    message.setBookingId(confirmation.getBookingId());
    return message;
  }

  private String buildBookingConfirmedJson(BookingConfirmation confirmation) {
    var template = "Booking [%d] successfully confirmed at [%s] for for event [%d]";
    var message = String.format(template, confirmation.getBookingId(),
        confirmation.getConfirmationDateTime()
        , confirmation.getEventId());
    var props = Map.of("status", "success", "message", message);

    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(props);
    } catch (Exception ex) {
      return props.toString();
    }
  }

  private String buildErrorJson(HttpStatus status, String error) {
    var props = Map.of("status", status.getReasonPhrase(), "error", error);

    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(props);
    } catch (Exception ex) {
      return props.toString();
    }
  }

  private boolean confirmBooking(BookingConfirmation confirmation) {
    var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    var expirationTime = confirmation.getConfirmationDateTime()
        .plusMinutes(confirmation.getDurationInMinutes())
        .truncatedTo(ChronoUnit.MINUTES);
    return now.isBefore(expirationTime);
  }

}

package piper1970.notificationservice.routehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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
import piper1970.notificationservice.exceptions.ConfirmationTimedOutException;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
public class BookingConfirmationHandler {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MessagePostingService messagePostingService;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Duration notificationTimeoutDuration;
  private final long maxRetries;
  private final Retry defaultRepositoryRetry;

  public BookingConfirmationHandler(BookingConfirmationRepository bookingConfirmationRepository,
      MessagePostingService messagePostingService, ObjectMapper objectMapper, Clock clock,
      Duration notificationTimeoutDuration,
      long maxRetries,
      Retry defaultRepositoryRetry) {
    this.bookingConfirmationRepository = bookingConfirmationRepository;
    this.messagePostingService = messagePostingService;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.notificationTimeoutDuration = notificationTimeoutDuration;
    this.maxRetries = maxRetries;
    this.defaultRepositoryRetry = defaultRepositoryRetry;
  }

  //region Main Handler

  public Mono<ServerResponse> handleConfirmation(ServerRequest request) {

    // guaranteed to be on path due to route-handler path template
    var confirmationString = request.pathVariable("confirmationString");
    log.debug("HandleConfirmation request received with token [{}]", confirmationString);

    try {

      // throws IllegalArgumentException if confirmationString not UUID
      var confimationUUID = UUID.fromString(confirmationString);

      return bookingConfirmationRepository.findByConfirmationString(confimationUUID)
          .timeout(notificationTimeoutDuration)
          .retryWhen(defaultRepositoryRetry) // retries only for timeouts
          .onErrorResume(
              ex -> handleConfirmationRepositoryTimeout(ex,
                  "finding booking confirmation for token [%s]".formatted(confimationUUID)))
          // avoid records already confirmed or expired
          .filter(confirmation -> confirmation.getConfirmationStatus()
              == ConfirmationStatus.AWAITING_CONFIRMATION)
          .switchIfEmpty(Mono.fromCallable(() -> {
            var message = "Booking confirmation string [%s] not found".formatted(
                confirmationString);
            log.warn(message);
            throw new ConfirmationNotFoundException(message);
          }))
          // main confirmation logic
          .flatMap(confirmation -> handleConfirmationLogic(confirmation, confirmationString))
          // retry if version of confirmation updated in repository since checked out
          .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500L))
              .filter(throwable -> throwable instanceof OptimisticLockingFailureException)
              .jitter(0.7D))
          .onErrorResume(ConfirmationNotFoundException.class, e -> {
            try {
              var body = buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), pd -> {
                pd.setTitle("Booking confirmation not found");
                pd.setType(
                    URI.create("http://notification-service/booking-confirmation-not-found"));
              });
              return ServerResponse.status(HttpStatus.NOT_FOUND)
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(body);
            } catch (JsonProcessingException ex) {
              // should never happen!!!
              log.error("JSON PROCESSING ERROR: response could not be properly built", e); 
              
              return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
          })
          // handle error logic if confirmation request timed out
          .onErrorResume(ConfirmationTimedOutException.class, e -> {
            log.warn("Repository timed out during processing of confirmation with token [{}]: {}",
                confirmationString, e.getMessage(),
                e);
            return handleServiceUnavailableResponse();
          });

    } catch (
        IllegalArgumentException e) { // error logic if confirmationString is not UUID-formatted
      var message = "[%s] is not a UUID-formatted string".formatted(confirmationString);
      log.warn(message, e);
      try {
        var responseBody = buildErrorResponse(HttpStatus.BAD_REQUEST, message, pd -> {
          pd.setTitle("Malformed confirmation-id");
          pd.setType(URI.create("http://notification-service/malformed-confirmation-id"));
        });
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(responseBody);
      } catch (JsonProcessingException ex) {
        // should never happen!!!
        log.error("JSON PROCESSING ERROR: response could not be properly built", e);
        
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
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

  /**
   * Build json response for successful confirmation
   */
  private String buildBookingConfirmedJson(BookingConfirmation confirmation)
      throws JsonProcessingException {
    var template = "Booking [%d] successfully confirmed at [%s] for for event [%d]";
    var message = String.format(template, confirmation.getBookingId(),
        confirmation.getConfirmationDateTime(),
        confirmation.getEventId());
    var props = Map.of("status", "success", "message", message);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(props);
  }

  /**
   * Build rfc9457-compliant JSON PROCESSING ERROR: response
   */
  private String buildErrorResponse(HttpStatus status, String message,
      Consumer<ProblemDetail> handler) throws JsonProcessingException {
    var problem = ProblemDetail.forStatusAndDetail(status, message);
    handler.accept(problem);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(problem);
  }

  //endregion JSON marshalling logic

  //region Confirmation Logic

  /**
   * Logic to handle both confirmation success or failure due to expiration behavior
   */
  private Mono<ServerResponse> handleConfirmationLogic(BookingConfirmation confirmation,
      String confirmationString) {

    if (confirmBooking(confirmation)) {

      var updatedConfirmation = confirmation.toBuilder()
          .confirmationStatus(ConfirmationStatus.CONFIRMED)
          .build();

      return bookingConfirmationRepository.save(updatedConfirmation)
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .timeout(notificationTimeoutDuration)
          .retryWhen(defaultRepositoryRetry) // retries for timeouts only
          .onErrorResume(
              ex -> handleConfirmationRepositoryTimeout(ex,
                  "saving confirmed booking confirmation for token [%s]".formatted(
                      confirmationString)))
          // post booking-confirmed message to kafka topic
          .flatMap(savedConfirmation -> {
            log.debug(
                "Booking confirmation [{}] successfully saved. Relaying success to BOOKING_CONFIRMED topic",
                savedConfirmation);
            var message = buildBookingConfirmedMessage(savedConfirmation);
            return messagePostingService.postBookingConfirmedMessage(message)
                .then(Mono.just(savedConfirmation));
          })
          // build/return OK/200 ServerResponse
          .flatMap(savedConfirmation -> {
            try {
              var json = buildBookingConfirmedJson(savedConfirmation);
              return ServerResponse.ok()
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(json);
            } catch (JsonProcessingException e) {
              // should never happen!!!
              log.error("JSON PROCESSING ERROR: response could not be properly built", e);

              return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
          });
    } else {

      var expiredConfirmation = confirmation.toBuilder()
          .confirmationStatus(ConfirmationStatus.EXPIRED)
          .build();

      var errorMessage = "Booking confirmation string [%s] has expired".formatted(
          confirmationString);

      log.warn(errorMessage);

      return bookingConfirmationRepository.save(expiredConfirmation)
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .timeout(notificationTimeoutDuration)
          .retryWhen(defaultRepositoryRetry) // retries for timeouts only
          .onErrorResume(
              ex -> handleConfirmationRepositoryTimeout(ex,
                  "saving expired booking confirmation for token [%s]".formatted(
                      confirmationString)))
          // post booking expired message to kafka topic
          .flatMap(bookingConfirmation -> {
            log.debug("Expired booking saved [{}]. Relaying failure to BOOKING_EXPIRED topic",
                bookingConfirmation);
            var message = buildBookingExpiredMessage(bookingConfirmation);
            return messagePostingService.postBookingExpiredMessage(message)
                .then(Mono.just(bookingConfirmation));
          })
          // build/return BAD_REQUEST/400 Server Response
          .flatMap(_ignored -> {
            try {
              var response = buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage, pd -> {
                pd.setTitle("Booking confirmation expired");
                pd.setType(URI.create("http://notification-service/booking-confirmation-expired"));
              });
              return ServerResponse.badRequest()
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(response);
            } catch (JsonProcessingException e) {
              // should never happen!!!
              log.error("JSON PROCESSING ERROR: response could not be properly built", e);

              return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
          });
    }
  }

  /**
   * Time-based confirmation validation test.
   *
   * @param confirmation record holding confirmationDateTime and durationInMinutes fields used for
   *                     logic
   * @return True if current time is before confirmationDateTime+durationInMinutes time. Otherwise,
   * false
   */
  private boolean confirmBooking(BookingConfirmation confirmation) {
    var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    var expirationTime = confirmation.getConfirmationDateTime()
        .plusMinutes(confirmation.getDurationInMinutes())
        .truncatedTo(ChronoUnit.MINUTES);
    return now.isBefore(expirationTime);
  }

  //endregion Confirmation Logic

  /**
   * Helper method for building/returning SERVICE_UNAVAILABLE/503 ServerResponse
   */
  private Mono<ServerResponse> handleServiceUnavailableResponse() {
    var message = "Service Unavailable. Please try again later.";
    try {
      var response = buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, message, pd -> {
        pd.setTitle("Service Unavailable");
        pd.setType(URI.create("http://notification-service/service-unavailable"));
      });
      return ServerResponse
          .status(HttpStatus.SERVICE_UNAVAILABLE)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(response);
    } catch (JsonProcessingException e) {
      // should never happen!!!
      log.error("JSON PROCESSING ERROR: response could not be properly built", e);
      
      return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Helper method for dealing with errors that may be due to Timeout/RetryExhausted errors
   */
  private Mono<BookingConfirmation> handleConfirmationRepositoryTimeout(Throwable ex,
      String subMessage) {
    String message;
    if (Exceptions.isRetryExhausted(ex)) {
      message = "attempting to fetch confirmation for token [%s]. Exhausted retries".formatted(
          subMessage);
    } else {
      message = "attempting to fetch confirmation for token [%s].".formatted(subMessage);
    }
    var timeoutMessage = String.format("Booking Confirmation timed out [over %d milliseconds] %s",
        notificationTimeoutDuration.toMillis(), message);

    return Mono.error(new ConfirmationTimedOutException(
        timeoutMessage, ex));
  }

  //endregion Helper Methods

}

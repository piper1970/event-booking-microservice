package piper1970.bookingservice.advice;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import piper1970.bookingservice.exceptions.BookingCancellationException;
import piper1970.bookingservice.exceptions.BookingCreationException;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.exceptions.BookingTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceUnavailableException;
import piper1970.bookingservice.service.DefaultEventRequestService;
import piper1970.eventservice.common.exceptions.EventForbiddenException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.exceptions.EventUnauthorizedException;
import piper1970.eventservice.common.exceptions.KafkaPostingException;
import piper1970.eventservice.common.exceptions.UnknownCauseException;

@ControllerAdvice
@Slf4j
public class BookingExceptionHandler {

  /**
   * Exception handler for {@link BookingNotFoundException} exceptions
   */
  @ExceptionHandler(BookingNotFoundException.class)
  public ProblemDetail handleException(BookingNotFoundException exc){
    log.warn("Booking not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.NOT_FOUND, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Not-found");
      pd.setType(URI.create("http://booking-service/problem/booking-not-found"));
    });
  }

  /**
   * Exception handler for {@link BookingCancellationException} exceptions
   */
  @ExceptionHandler(BookingCancellationException.class)
  public ProblemDetail handleException(BookingCancellationException exc){
    log.warn("Attempt to cancel booking failed [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.CONFLICT, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Cannot-Be-Cancelled");
      pd.setType(URI.create("http://booking-service/problem/booking-cannot-be-cancelled"));
    });
  }

  /**
   * Exception handler for {@link BookingCreationException} exceptions
   */
  @ExceptionHandler(BookingCreationException.class)
  public ProblemDetail handleException(BookingCreationException exc){
    log.warn("Attempt to create booking failed [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.UNPROCESSABLE_ENTITY, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Creation-Failed");
      pd.setType(URI.create("http://booking-service/problem/booking-creation-failed"));
    });
  }

  /**
   * Exception handler for {@link EventNotFoundException} exceptions.
   * <p>
   * Thrown when inner call to event-service api returns a 404/Not Found.
   */
  @ExceptionHandler(EventNotFoundException.class)
  public ProblemDetail handleException(EventNotFoundException exc){
    log.warn("Event not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.BAD_REQUEST, exc.getMessage(), pd -> {
      pd.setTitle("Invalid Event ID: Event Not Found");
      pd.setType(URI.create("http://booking-service/problem/event-not-found"));
    });
  }

  /**
   * Exception handler for {@link WebExchangeBindException} exceptions. Thrown when bean validation fails.
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public ProblemDetail handleException(WebExchangeBindException exc){
    var message = exc.getBindingResult().getAllErrors()
        .stream()
        .map(DefaultMessageSourceResolvable::getDefaultMessage)
        .collect(Collectors.joining("; "));

    log.warn("Validation errors occurred [{}]", message, exc);

    return buildProblemDetail(HttpStatus.BAD_REQUEST, message, pd -> {
      pd.setTitle("Booking-Validation-Errors");
      pd.setType(URI.create("http://booking-service/problem/booking-validation-errors"));
    });
  }

  /**
   * Exception handler for {@link BookingTimeoutException} exceptions
   */
  @ExceptionHandler(BookingTimeoutException.class)
  public ProblemDetail handleException(BookingTimeoutException exc){
    log.error("Booking repository action timed out [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Repository-Temporarily-Unavailable");
      pd.setType(URI.create("http://booking-service/problem/booking-repository-temporarily-unavailable"));
    });
  }

  /**
   * Exception handler for {@link EventRequestServiceTimeoutException} exceptions
   */
  @ExceptionHandler(EventRequestServiceTimeoutException.class)
  public ProblemDetail handleException(EventRequestServiceTimeoutException exc){
    log.error("event-request-service timed out [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, exc.getMessage(), pd -> {
      pd.setTitle("Event-Request-Service-Call-Exceeded-Allotted-Time");
      pd.setType(URI.create("http://booking-service/problem/event-request-service-call-exceeded-allotted-time"));
    });
  }

  /**
   * Exception handler for {@link EventRequestServiceUnavailableException} exceptions
   */
  @ExceptionHandler(EventRequestServiceUnavailableException.class)
  public ProblemDetail handleException(EventRequestServiceUnavailableException exc){
    log.error("event-request-service temporarily unavailable [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, exc.getMessage(), pd -> {
      pd.setTitle("Unable-To-Contact-Event-Service");
      pd.setType(URI.create("http://booking-service/problem/unable-to-contact-event-service"));
    });
  }

  /**
   * Exception handler for {@link EventForbiddenException} exceptions.
   * <p>
   * Thrown when inner call to event-service API returns a 403/Forbidden response.
   */
  @ExceptionHandler(EventForbiddenException.class)
  public ProblemDetail handleException(EventForbiddenException exc){
    // at this stage, token already authorized through booking controller (auth=MEMBER).
    // event-service call requires MEMBER authorization as well.
    // if event-service responds with Forbidden(403), something in the universe is amiss.
    // if token expired, a Unauthorized(401) would be returned, not a 403.
    // this edge case should not happen...
    log.error("Attempt to access event-service with currently authorized token failed. Service returned Forbidden(403). [{}].", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.FORBIDDEN, exc.getMessage(), pd -> {
      pd.setTitle("Forbidden-Access-To-Event-Service");
      pd.setType(URI.create("http://booking-service/problem/forbidden-access-to-event-service"));
    });
  }

  /**
   * Exception handler for {@link EventUnauthorizedException} exceptions.
   * <p>
   * Thrown when inner call to event-service API returns a 401/UnAuthorized response.
   */
  @ExceptionHandler(EventUnauthorizedException.class)
  public ProblemDetail handleException(EventUnauthorizedException exc){

    // at this stage, token passed to event-request-service should have already passed authentication through booking-controller.
    // if failed, possible issues are token expiration or token corruption somewhere in transit
    log.error("Attempt to access event-service using currently authorized token failed. Service returned Unauthorized 401). [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.UNAUTHORIZED, exc.getMessage(), pd -> {
      pd.setTitle("Unauthorized-Access-To-Event-Service");
      pd.setType(URI.create("http://booking-service/problem/unauthorized-access-to-event-service"));
    });
  }

  /**
   * Exception handler for {@link UnknownCauseException} exceptions.
   * <p>
   * Thrown by {@link DefaultEventRequestService} when unrecognized 400 status code is returned from call to event-service api.
   * <p>
   * Recognized 400 responses are:
   * <ul>
   *   <li>404: Not Found</li>
   *   <li>401: Unauthorized</li>
   *   <li>403: Forbidden</li>
   * </ul>
   */
  @ExceptionHandler(UnknownCauseException.class)
  public ProblemDetail handleException(UnknownCauseException exc){
    log.error("An exception has occurred for unknown reasons [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage(), pd -> {
      pd.setTitle("Unknown-Cause-Exception");
      pd.setType(URI.create("http://booking-service/problem/unknown-cause-exception"));
    });
  }

  /**
   * Exception handler for {@link KafkaPostingException} exceptions.
   * <p>
   * Thrown when kafka times out attempting to post a message.
   */
  @ExceptionHandler(KafkaPostingException.class)
  public ProblemDetail handleException(KafkaPostingException exc) {
    log.warn("Booking posting failed. [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, exc.getMessage(), pd -> {
      pd.setTitle("Booking message posting failed");
      pd.setType(URI.create("http://booking-service/problem/booking-message-posting-failed"));
    });
  }

  /**
   * Exception handler for {@link WebClientResponseException} exceptions. StatusCode from
   * WebClientResponseException used directly in the ProblemDetail response
   */
  @ExceptionHandler(WebClientResponseException.class)
  public ProblemDetail handleException(WebClientResponseException exc){
    log.error("Problems accessing event-service [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.valueOf(exc.getStatusCode().value()), exc.getMessage(), pd -> {
      pd.setTitle("Events-Service-Not-Available");
      pd.setType(URI.create("http://booking-service/problem/events-service-not-available"));
    });
  }

  /**
   * Helper method for building base portion of {@link ProblemDetail} message.
   * 
   * @param status HttpStatus to apply to ProblemDetail object
   * @param message Message to apply to ProblemDetail object
   * @param handler ProblemDetail consumer used to apply changes to ProblemDetail object
   * @return adjusted ProblemDetail object for displaying consistent json error messages
   */
  private ProblemDetail buildProblemDetail(HttpStatus status, String message,
      Consumer<ProblemDetail> handler) {
    var problem = ProblemDetail.forStatusAndDetail(status, message);
    handler.accept(problem);
    return problem;
  }

}

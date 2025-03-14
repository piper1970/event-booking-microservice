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
import piper1970.bookingservice.exceptions.BookingDeletionException;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.exceptions.BookingTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceUnavailableException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.exceptions.EventForbiddenException;
import piper1970.eventservice.common.exceptions.EventUnauthorizedException;
import piper1970.eventservice.common.exceptions.UnknownCauseException;

@ControllerAdvice
@Slf4j
public class BookingExceptionHandler {

  @ExceptionHandler(BookingNotFoundException.class)
  public ProblemDetail handleException(BookingNotFoundException exc){
    log.warn("Booking not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.NOT_FOUND, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Not-found");
      pd.setType(URI.create("http://booking-service/problem/booking-not-found"));
    });
  }

  @ExceptionHandler(BookingCancellationException.class)
  public ProblemDetail handleException(BookingCancellationException exc){
    log.warn("Attempt to cancel booking failed [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.CONFLICT, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Cannot-Be-Cancelled");
      pd.setType(URI.create("http://booking-service/problem/booking-cannot-be-cancelled"));
    });
  }

  @ExceptionHandler(BookingDeletionException.class)
  public ProblemDetail handleException(BookingDeletionException exc){
    log.warn("Attempt to delete booking failed [{}]", exc.getMessage(), exc);
    return buildProblemDetail(HttpStatus.CONFLICT, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Deletion-Failed");
      pd.setType(URI.create("http://booking-service/problem/booking-deletion-failed"));
    });
  }

  @ExceptionHandler(BookingCreationException.class)
  public ProblemDetail handleException(BookingCreationException exc){
    log.warn("Attempt to create booking failed [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.UNPROCESSABLE_ENTITY, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Creation-Failed");
      pd.setType(URI.create("http://booking-service/problem/booking-creation-failed"));
    });
  }

  @ExceptionHandler(EventNotFoundException.class)
  public ProblemDetail handleException(EventNotFoundException exc){
    log.warn("Event not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.GONE, exc.getMessage(), pd -> {
      pd.setTitle("Event-Not-Available-For-Booking");
      pd.setType(URI.create("http://booking-service/problem/event-not-available"));
    });
  }

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

  @ExceptionHandler(BookingTimeoutException.class)
  public ProblemDetail handleException(BookingTimeoutException exc){
    log.error("Booking repository action timed out [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage(), pd -> {
      pd.setTitle("Booking-Repository-Temporarily-Unavailable");
      pd.setType(URI.create("http://booking-service/problem/booking-repository-temporarily-unavailable"));
    });
  }

  @ExceptionHandler(EventRequestServiceTimeoutException.class)
  public ProblemDetail handleException(EventRequestServiceTimeoutException exc){
    log.error("event-request-service timed out [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage(), pd -> {
      pd.setTitle("Event-Request-Service-Call-Exceeded-Allotted-Time");
      pd.setType(URI.create("http://booking-service/problem/event-request-service-call-exceeded-allotted-time"));
    });
  }

  @ExceptionHandler(EventRequestServiceUnavailableException.class)
  public ProblemDetail handleException(EventRequestServiceUnavailableException exc){
    log.error("event-request-service temporarily unavailable [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage(), pd -> {
      pd.setTitle("Unable-To-Contact-Event-Service");
      pd.setType(URI.create("http://booking-service/problem/unable-to-contact-event-service"));
    });
  }

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

  @ExceptionHandler(UnknownCauseException.class)
  public ProblemDetail handleException(UnknownCauseException exc){
    log.error("An exception has occurred for unknown reasons [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage(), pd -> {
      pd.setTitle("Unknown-Cause-Exception");
      pd.setType(URI.create("http://booking-service/problem/unknown-cause-exception"));
    });
  }

  @ExceptionHandler(WebClientResponseException.class)
  public ProblemDetail handleException(WebClientResponseException exc){
    log.error("Problems accessing event-service [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exc.getMessage(), pd -> {
      pd.setTitle("Events-Service-Not-Available");
      pd.setType(URI.create("http://booking-service/problem/events-service-not-available"));
    });
  }

  // Helper method for building base portion of problem-detail message
  private ProblemDetail buildProblemDetail(HttpStatus status, String message,
      Consumer<ProblemDetail> handler) {
    var problem = ProblemDetail.forStatusAndDetail(status, message);
    handler.accept(problem);
    return problem;
  }

}

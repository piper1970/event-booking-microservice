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
import piper1970.eventservice.common.exceptions.BookingCancellationException;
import piper1970.eventservice.common.exceptions.BookingNotFoundException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;

@ControllerAdvice
@Slf4j
public class BookingExceptionHandler {

  @ExceptionHandler(BookingNotFoundException.class)
  public ProblemDetail handleException(BookingNotFoundException exc){
    log.warn("Booking not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.NOT_FOUND, exc.getMessage(), pd -> {
      pd.setTitle("Booking not found");
      pd.setType(URI.create("http://booking-service/problem/booking-not-found"));
    });
  }

  @ExceptionHandler(BookingCancellationException.class)
  public ProblemDetail handleException(BookingCancellationException exc){
    log.warn("Attempt to cancel booking failed [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.CONFLICT, exc.getMessage(), pd -> {
      pd.setTitle("Booking cannot be cancelled");
      pd.setType(URI.create("http://booking-service/problem/booking-cannot-be-cancelled"));
    });
  }

  @ExceptionHandler(EventNotFoundException.class)
  public ProblemDetail handleException(EventNotFoundException exc){
    log.warn("Event not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.GONE, exc.getMessage(), pd -> {
      pd.setTitle("Event not available for booking");
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
      pd.setTitle("Validation Errors");
      pd.setType(URI.create("http://booking-service/problem/booking-validation-errors"));
    });
  }

  @ExceptionHandler(WebClientResponseException.class)
  public ProblemDetail handleException(WebClientResponseException exc){
    log.warn("Problems accessing event-service [{}]", exc.getMessage(), exc);

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

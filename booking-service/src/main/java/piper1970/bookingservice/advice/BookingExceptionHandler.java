package piper1970.bookingservice.advice;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import piper1970.eventservice.common.exceptions.BookingNotFoundException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;

@ControllerAdvice
public class BookingExceptionHandler {

  @ExceptionHandler(BookingNotFoundException.class)
  public ProblemDetail handleException(BookingNotFoundException nfe){
    return buildProblemDetail(HttpStatus.NOT_FOUND, nfe.getMessage(), pd -> {
      pd.setTitle("Booking not found");
      pd.setType(URI.create("http://booking-service/problem/booking-not-found"));
    });
  }

  @ExceptionHandler(EventNotFoundException.class)
  public ProblemDetail handleException(EventNotFoundException enfe){
    return buildProblemDetail(HttpStatus.GONE, enfe.getMessage(), pd -> {
      pd.setTitle("Event not available for booking");
      pd.setType(URI.create("http://booking-service/problem/event-not-available"));
    });
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public ProblemDetail handleException(WebExchangeBindException be){
    var message = be.getBindingResult().getAllErrors()
        .stream()
        .map(DefaultMessageSourceResolvable::getDefaultMessage)
        .collect(Collectors.joining("; "));
    return buildProblemDetail(HttpStatus.BAD_REQUEST, message, pd -> {
      pd.setTitle("Validation Errors");
      pd.setType(URI.create("http://booking-service/problem/booking-validation-errors"));
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

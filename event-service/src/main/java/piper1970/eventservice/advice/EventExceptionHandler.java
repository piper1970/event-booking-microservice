package piper1970.eventservice.advice;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import piper1970.eventservice.exceptions.EventNotFoundException;

@ControllerAdvice
public class EventExceptionHandler {

  @ExceptionHandler(EventNotFoundException.class)
  public ProblemDetail handleNotFound(EventNotFoundException ex) {
    return buildProblemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), pd -> {
      pd.setTitle("Event not found");
      pd.setType(URI.create("http://event-service/problem/event-not-found"));
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
      pd.setType(URI.create("http://event-service/problem/event-validation-errors"));
    });
  }

  // Title field must be unique for posting new events
  @ExceptionHandler(DuplicateKeyException.class)
  public ProblemDetail handleException(DuplicateKeyException dke){
    var message = "Duplicate [title] field found in the system. Please choose another.";
    return buildProblemDetail(HttpStatus.CONFLICT, message, pd -> {
      pd.setTitle("Duplicate [title]");
      pd.setType(URI.create("http://event-service/problem/event-duplicate-title"));
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

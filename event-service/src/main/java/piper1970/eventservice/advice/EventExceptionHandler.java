package piper1970.eventservice.advice;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.exceptions.KafkaPostingException;
import piper1970.eventservice.exceptions.EventCancellationException;
import piper1970.eventservice.exceptions.EventTimeoutException;
import piper1970.eventservice.exceptions.EventUpdateException;

@ControllerAdvice
@Slf4j
public class EventExceptionHandler {

  /** 
   * Exception handler for {@link EventNotFoundException} exceptions.
   * <p>
   * Thrown when a specific event cannot be found.
   */
  @ExceptionHandler(EventNotFoundException.class)
  public ProblemDetail handleNotFound(EventNotFoundException exc) {
    log.warn("Event not found [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.NOT_FOUND, exc.getMessage(), pd -> {
      pd.setTitle("Event not found");
      pd.setType(URI.create("http://event-service/problem/event-not-found"));
    });
  }

  /**
   * Exception handler for {@link EventCancellationException} exceptions 
   */
  @ExceptionHandler(EventCancellationException.class)
  public ProblemDetail handleCancellation(EventCancellationException exc) {
    log.warn("Event cancellation failed. [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.BAD_REQUEST, exc.getMessage(), pd -> {
      pd.setTitle("Event cancellation failed");
      pd.setType(URI.create("http://event-service/problem/event-cancellation-failed"));
    });
  }

  /**
   * Exception handler for {@link EventTimeoutException} exceptions 
   */
  @ExceptionHandler(EventTimeoutException.class)
  public ProblemDetail handleTimeout(EventTimeoutException exc) {
    log.error("Timeout occurred accessing events repository. [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, exc.getMessage(), pd -> {
      pd.setTitle("Event-Repository-Temporarily-Unavailable");
      pd.setType(URI.create("http://event-service/problem/event-repository-temporarily-unavailable"));
    });
  }

  /**
   * Exception handler for {@link EventUpdateException} exceptions
   */
  @ExceptionHandler(EventUpdateException.class)
  public ProblemDetail handleTransition(EventUpdateException exc) {
    log.warn("Event update failed. [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.BAD_REQUEST, exc.getMessage(), pd -> {
      pd.setTitle("Event update failed");
      pd.setType(URI.create("http://event-service/problem/event-update-failed"));
    });
  }

  /**
   * Exception handler for {@link KafkaPostingException} exceptions.
   * <p>
   * Thrown when kafka times out attempting to post a message.
   */
  @ExceptionHandler(KafkaPostingException.class)
  public ProblemDetail handlePosting(KafkaPostingException exc) {
    log.warn("Event posting failed. [{}]", exc.getMessage(), exc);

    return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, exc.getMessage(), pd -> {
      pd.setTitle("Event message posting failed");
      pd.setType(URI.create("http://event-service/problem/event-message-posting-failed"));
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
      pd.setTitle("Validation Errors");
      pd.setType(URI.create("http://event-service/problem/event-validation-errors"));
    });
  }

  /**
   * Exception handler for {@link DuplicateKeyException} exceptions.
   * <p>
   * Title field must be unique for posting new events.
   */
  @ExceptionHandler(DuplicateKeyException.class)
  public ProblemDetail handleException(DuplicateKeyException exc){
    log.warn("Duplicate key [{}]", exc.getMessage(), exc);

    var message = "Duplicate [title] field found in the system. Please choose another.";

    return buildProblemDetail(HttpStatus.CONFLICT, message, pd -> {
      pd.setTitle("Duplicate [title]");
      pd.setType(URI.create("http://event-service/problem/event-duplicate-title"));
    });
  }

  // TODO: consider exporting this into a helper file, since same code used in multiple ControllerAdvice files
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

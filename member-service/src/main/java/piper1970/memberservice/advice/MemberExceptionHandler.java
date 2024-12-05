package piper1970.memberservice.advice;

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
import piper1970.memberservice.exceptions.MemberNotFoundException;

@ControllerAdvice
public class MemberExceptionHandler {

  @ExceptionHandler(MemberNotFoundException.class)
  public ProblemDetail handleNotFound(MemberNotFoundException ex) {
    return buildProblemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), pd -> {
      pd.setTitle("Member not found");
      pd.setType(URI.create("http://member-service/problem/member-not-found"));
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
      pd.setType(URI.create("http://member-service/problem/member-validation-errors"));
    });
  }

  // username field must be unique for posting new events
  @ExceptionHandler(DuplicateKeyException.class)
  public ProblemDetail handleException(DuplicateKeyException dke){
    var message = "Duplicate [username] field found in the system. Please choose another.";
    return buildProblemDetail(HttpStatus.CONFLICT, message, pd -> {
      pd.setTitle("Duplicate [username]");
      pd.setType(URI.create("http://member-service/problem/member-duplicate-username"));
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

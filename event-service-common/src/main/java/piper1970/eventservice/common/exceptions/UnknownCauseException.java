package piper1970.eventservice.common.exceptions;

public class UnknownCauseException extends RuntimeException {

  public UnknownCauseException(String message) {
    super(message);
  }

  public UnknownCauseException(String message, Throwable cause) {
    super(message, cause);
  }
}

package piper1970.eventservice.common.exceptions;

public class EventForbiddenException extends RuntimeException {

  public EventForbiddenException(String message) {
    super(message);
  }

  public EventForbiddenException(String message, Throwable cause) {
    super(message, cause);
  }
}

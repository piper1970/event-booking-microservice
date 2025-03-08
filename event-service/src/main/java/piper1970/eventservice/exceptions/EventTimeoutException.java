package piper1970.eventservice.exceptions;

public class EventTimeoutException extends RuntimeException {

  public EventTimeoutException(String message) {
    super(message);
  }

  public EventTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

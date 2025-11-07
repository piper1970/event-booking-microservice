package piper1970.eventservice.exceptions;

/**
 * Exception thrown by the event service when a timeout occurs trying to
 * access or create an event.
 */
public class EventTimeoutException extends RuntimeException {
  public EventTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

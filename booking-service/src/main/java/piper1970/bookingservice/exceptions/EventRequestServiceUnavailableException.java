package piper1970.bookingservice.exceptions;

/**
 * Exception thrown by the event-service client when a timeout occurs trying to
 * access the service for a given event.
 */
public class EventRequestServiceUnavailableException extends RuntimeException {

  public EventRequestServiceUnavailableException(String message) {
    super(message);
  }

  public EventRequestServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

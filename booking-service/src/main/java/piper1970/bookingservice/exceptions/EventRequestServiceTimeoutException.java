package piper1970.bookingservice.exceptions;

/**
 * Exception thrown by the event-service web client when the event-service returns a 500-level response.
 */
public class EventRequestServiceTimeoutException extends RuntimeException {

  public EventRequestServiceTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

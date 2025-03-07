package piper1970.bookingservice.exceptions;

public class EventRequestServiceUnavailableException extends RuntimeException {

  public EventRequestServiceUnavailableException(String message) {
    super(message);
  }

  public EventRequestServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

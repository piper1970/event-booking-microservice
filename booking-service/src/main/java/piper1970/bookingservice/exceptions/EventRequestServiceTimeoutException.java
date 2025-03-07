package piper1970.bookingservice.exceptions;

public class EventRequestServiceTimeoutException extends RuntimeException {


  public EventRequestServiceTimeoutException(String message) {
    super(message);
  }

  public EventRequestServiceTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

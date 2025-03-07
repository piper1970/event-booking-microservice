package piper1970.bookingservice.exceptions;

public class BookingTimeoutException extends RuntimeException {
  public BookingTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

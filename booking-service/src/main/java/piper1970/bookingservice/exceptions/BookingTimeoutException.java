package piper1970.bookingservice.exceptions;

/**
 * Exception thrown by the booking service when a timeout occurs trying to
 * access or create a booking.
 */
public class BookingTimeoutException extends RuntimeException {
  public BookingTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

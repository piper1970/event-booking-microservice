package piper1970.bookingservice.exceptions;

/**
 * Exception thrown when user attempts to find a booking by id and username fails. It can also
 * be thrown when attempting to cancel a booking that is not in the system.
 */
public class BookingNotFoundException extends RuntimeException {
  public BookingNotFoundException(String message) {
    super(message);
  }
}

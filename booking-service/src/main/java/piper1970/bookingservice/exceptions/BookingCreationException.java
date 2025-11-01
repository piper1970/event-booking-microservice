package piper1970.bookingservice.exceptions;

/**
 * Exception thrown when user attempts to create a booking to an event that has already started, or
 * has no available seats.
 */
public class BookingCreationException extends RuntimeException {
  public BookingCreationException(String message) {
    super(message);
  }
}

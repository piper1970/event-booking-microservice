package piper1970.bookingservice.exceptions;

/**
 * Exception thrown when user attempts to cancel a booking when the event has already started, or
 * the booking has already been cancelled.
 */
public class BookingCancellationException  extends RuntimeException{
  public BookingCancellationException(String message) {
    super(message);
  }
}

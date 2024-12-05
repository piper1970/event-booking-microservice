package piper1970.bookingservice.exceptions;

public class BookingNotFoundException extends RuntimeException {
  public BookingNotFoundException(String message) {
    super(message);
  }
}

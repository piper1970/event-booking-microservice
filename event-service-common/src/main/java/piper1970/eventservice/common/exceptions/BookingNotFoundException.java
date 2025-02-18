package piper1970.eventservice.common.exceptions;

public class BookingNotFoundException extends RuntimeException {
  public BookingNotFoundException(String message) {
    super(message);
  }
}

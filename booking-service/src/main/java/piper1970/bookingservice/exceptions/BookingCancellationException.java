package piper1970.bookingservice.exceptions;

public class BookingCancellationException  extends RuntimeException{
  public BookingCancellationException(String message) {
    super(message);
  }
}

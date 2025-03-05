package piper1970.eventservice.exceptions;

public class EventCancellationException extends RuntimeException {
  public EventCancellationException(String message) {
    super(message);
  }
}

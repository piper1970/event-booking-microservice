package piper1970.eventservice.exceptions;

public class EventNotFoundException extends RuntimeException {
  public EventNotFoundException(String message) {
    super(message);
  }
}

package piper1970.eventservice.common.exceptions;

public class EventNotFoundException extends RuntimeException {
  public EventNotFoundException(String message) {
    super(message);
  }
}

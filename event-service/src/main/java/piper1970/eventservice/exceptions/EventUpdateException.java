package piper1970.eventservice.exceptions;

/**
 * Exception thrown when the event owner tries to update an event that is either cancelled
 * or has already started.
 */
public class EventUpdateException extends RuntimeException {
  public EventUpdateException(String message) {
    super(message);
  }
}

package piper1970.eventservice.exceptions;

/**
 * Exception thrown when owner of event attempts to cancel an event already in progress.
 */
public class EventCancellationException extends RuntimeException {
  public EventCancellationException(String message) {
    super(message);
  }
}

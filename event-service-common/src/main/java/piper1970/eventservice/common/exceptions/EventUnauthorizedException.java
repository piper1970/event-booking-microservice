package piper1970.eventservice.common.exceptions;

public class EventUnauthorizedException extends RuntimeException {

  public EventUnauthorizedException(String message) {
    super(message);
  }

  public EventUnauthorizedException(String message, Throwable cause) {
    super(message, cause);
  }
}

package piper1970.notificationservice.exceptions;

/**
 * Exception thrown when system times out during attempt to handle clicking of confirmation
 */
public class ConfirmationTimedOutException extends RuntimeException {
  public ConfirmationTimedOutException(String message, Throwable cause) {
    super(message, cause);
  }
}

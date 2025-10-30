package piper1970.notificationservice.exceptions;

/**
 * Exception thrown when a user clicks on a confirmation that either expired or was already confirmed
 */
public class ConfirmationNotFoundException extends RuntimeException {
  public ConfirmationNotFoundException(String message) {
    super(message);
  }
}

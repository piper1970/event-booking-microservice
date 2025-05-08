package piper1970.notificationservice.exceptions;

public class ConfirmationTimedOutException extends RuntimeException {

  public ConfirmationTimedOutException(String message) {
    super(message);
  }

  public ConfirmationTimedOutException(String message, Throwable cause) {
    super(message, cause);
  }
}

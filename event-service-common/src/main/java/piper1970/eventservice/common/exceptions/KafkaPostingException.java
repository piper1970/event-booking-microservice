package piper1970.eventservice.common.exceptions;

public class KafkaPostingException extends RuntimeException {
  public KafkaPostingException(String message, Throwable cause) {
    super(message, cause);
  }
}

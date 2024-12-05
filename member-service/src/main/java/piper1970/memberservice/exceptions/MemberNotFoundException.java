package piper1970.memberservice.exceptions;

public class MemberNotFoundException extends RuntimeException {
  public MemberNotFoundException(String message) {
    super(message);
  }
}

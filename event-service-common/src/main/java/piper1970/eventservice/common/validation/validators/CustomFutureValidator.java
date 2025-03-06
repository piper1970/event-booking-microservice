package piper1970.eventservice.common.validation.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.LocalDateTime;
import piper1970.eventservice.common.validation.annotations.CustomFuture;

public class CustomFutureValidator implements ConstraintValidator<CustomFuture, LocalDateTime> {

  private final Clock clock;

  public CustomFutureValidator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    return value.isAfter(now);
  }
}

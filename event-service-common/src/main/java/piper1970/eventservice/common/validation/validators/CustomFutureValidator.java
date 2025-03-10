package piper1970.eventservice.common.validation.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.LocalDateTime;
import piper1970.eventservice.common.validation.annotations.CustomFuture;
import piper1970.eventservice.common.validation.validators.context.ValidationContextProvider;

public class CustomFutureValidator implements ConstraintValidator<CustomFuture, LocalDateTime> {

  private Clock clock;

  @Override
  public void initialize(CustomFuture constraintAnnotation) {
    clock = ValidationContextProvider.getBean(Clock.class);
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

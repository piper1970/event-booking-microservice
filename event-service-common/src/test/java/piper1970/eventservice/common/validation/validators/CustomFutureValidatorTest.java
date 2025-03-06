package piper1970.eventservice.common.validation.validators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.hibernate.validator.internal.util.annotation.ConstraintAnnotationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import piper1970.eventservice.common.validation.annotations.CustomFuture;

@ExtendWith(MockitoExtension.class)
class CustomFutureValidatorTest {

  private CustomFutureValidator validator;

  @Mock
  ConstraintValidatorContext context;

  @Mock
  Clock clock;

  @BeforeEach
  void setUp() {

    when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    when(clock.instant()).thenReturn(Instant.parse("2025-03-05T18:35:00Z"));

    validator = new CustomFutureValidator(clock);

    ConstraintAnnotationDescriptor.Builder<CustomFuture> builder = new ConstraintAnnotationDescriptor.Builder<>(CustomFuture.class);
    builder.setMessage("You lose...");

    validator.initialize(builder.build().getAnnotation());

  }

  @Test
  void isValid() {
    // future value
    var date1 = LocalDateTime.now(clock).plusMinutes(1);
    boolean isValid1 = validator.isValid(date1, context);
    assertTrue(isValid1, "%s is in the future, so it should be valid".formatted(date1));

    // at the exact time
    var date2 = LocalDateTime.now(clock);
    boolean isValid2 = validator.isValid(date2, context);
    assertFalse(isValid2, "%s is current, so it should not invalid".formatted(date2));

    // in the past
    var date3 = LocalDateTime.now(clock).minusMinutes(1);
    boolean isValid3 = validator.isValid(date3, context);
    assertFalse(isValid3, "%s is in the past, so it should not invalid".formatted(date3));
  }
}
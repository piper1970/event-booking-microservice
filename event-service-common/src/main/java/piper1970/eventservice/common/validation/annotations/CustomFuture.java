package piper1970.eventservice.common.validation.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;

@Constraint(validatedBy = CustomFutureValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomFuture {
  String message();
  Class<?>[] groups() default { };
  Class<? extends Payload>[] payload() default { };
}

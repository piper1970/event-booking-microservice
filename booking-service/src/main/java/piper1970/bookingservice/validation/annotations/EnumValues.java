package piper1970.bookingservice.validation.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import piper1970.bookingservice.validation.validators.EnumValuesValidator;

/**
 * Validation annotation used for binding String values to given Enum class. Allows for nulls
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = EnumValuesValidator.class)
public @interface EnumValues {

  Class<? extends Enum<?>> enumClass();

  String message();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}

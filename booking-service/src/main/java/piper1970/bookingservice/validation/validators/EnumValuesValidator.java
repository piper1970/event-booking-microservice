package piper1970.bookingservice.validation.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;
import piper1970.bookingservice.validation.annotations.EnumValues;

/**
 * Validator for ensuring the String value to test is one of the names() for the given Enum
 *
 */
public class EnumValuesValidator implements ConstraintValidator<EnumValues, String> {

  List<String> valuesList;

  @SuppressWarnings("rawtypes")
  @Override
  public void initialize(EnumValues constraintAnnotation) {
    valuesList = new ArrayList<>();
    Class<? extends Enum<?>> enumClass = constraintAnnotation.enumClass();

    Enum[] enumConstants = enumClass.getEnumConstants();
    for (Enum e : enumConstants) {
      valuesList.add(e.name());
    }
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return valuesList.contains(value);
  }
}

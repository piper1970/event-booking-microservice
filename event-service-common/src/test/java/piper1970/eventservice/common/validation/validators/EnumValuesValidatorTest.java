package piper1970.eventservice.common.validation.validators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.internal.util.annotation.ConstraintAnnotationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import piper1970.eventservice.common.validation.annotations.EnumValues;

@ExtendWith(MockitoExtension.class)
class EnumValuesValidatorTest {

  private  EnumValuesValidator validator;

  @Mock
  ConstraintValidatorContext context;

  @BeforeEach
  void setUp() {
    validator = new EnumValuesValidator();

    ConstraintAnnotationDescriptor.Builder<EnumValues> builder = new ConstraintAnnotationDescriptor.Builder<>(EnumValues.class);
    builder.setMessage("You lose...");
    builder.setAttribute("enumClass", TEST_VALUES.class);

    validator.initialize(builder.build().getAnnotation());

  }

  @Test
  void isValid() {

    String test1 = TEST_VALUES.MY_TEST_VALUE_1.name();
    boolean isValid = validator.isValid(test1, context);
    assertTrue(isValid, "%s Should be valid".formatted(test1));

    String test2 = TEST_VALUES.MY_TEST_VALUE_2.name();
    boolean isValid2 = validator.isValid(test2, context);
    assertTrue(isValid2, "%s Should be valid".formatted(test2));

    String test3 = "BAD_VALUE"; //
    boolean isValid3 = validator.isValid(test3, context);
    assertFalse(isValid3, "%s Should be valid".formatted(test3));

  }

  enum TEST_VALUES {
    MY_TEST_VALUE_1,
    MY_TEST_VALUE_2
  }
}
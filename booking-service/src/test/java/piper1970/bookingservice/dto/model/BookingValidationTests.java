package piper1970.bookingservice.dto.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import piper1970.bookingservice.dto.model.BookingCreateRequest.BookingCreateRequestBuilder;
import piper1970.bookingservice.validation.validators.EnumValuesValidator;
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;
import piper1970.eventservice.common.validation.validators.context.ValidationContextProvider;

@ExtendWith(SpringExtension.class)
@TestClassOrder(OrderAnnotation.class)
@Order(5)
public class BookingValidationTests {

  private static final Clock clock = Clock.systemDefaultZone();

  //region BookingCreateRequest Validation Checks

  @DisplayName("BookingCreateRequest validation assertions")
  @ParameterizedTest(name = "{index} ==> {0}")
  @MethodSource("namedCreateRequestArguments")
  void testBookingCreateRequest(String description, Consumer<BookingCreateRequestBuilder> consumer,
      boolean isValid) {

    var bldr = goodBookingCreateRequestBuilder();
    consumer.accept(bldr);
    var request = bldr.build();

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();

      var violations = validator.validate(request);

      System.out.println(convertCreateViolationsToString(violations));

      assertThat(violations).isNotNull();

      if (isValid) {
        assertThat(violations).withFailMessage(description).isEmpty();
      } else {
        assertThat(violations).withFailMessage(description).isNotEmpty();
      }
    }
  }

  //endregion

  //region Helper Methods

  private BookingCreateRequest.BookingCreateRequestBuilder goodBookingCreateRequestBuilder() {
    return BookingCreateRequest.builder()
        .eventId(27)
        .email(null) // must be null to pass validation
        .username(null); // must be null to pass validation
  }

  private static Stream<Arguments> namedCreateRequestArguments() {

    record MethodArgs(String message, Consumer<BookingCreateRequestBuilder> builder,
                      boolean isValid) {

      static MethodArgs of(String message, Consumer<BookingCreateRequestBuilder> builder,
          boolean isValid) {
        return new MethodArgs(message, builder, isValid);
      }
    }

    List<MethodArgs> methods = new ArrayList<>();

    methods.add(MethodArgs.of("Validation will fail if eventId is null",
        bldr -> bldr.eventId(null), false));
    methods.add(MethodArgs.of("Validation will fail if eventId is negative",
        bldr -> bldr.eventId(-1), false));
    methods.add(MethodArgs.of("Validation will pass if eventId is positive",
        bldr -> bldr.eventId(1), true));
    methods.add(MethodArgs.of("Validation will fail if eventId is zero",
        bldr -> bldr.eventId(0), false));
    methods.add(MethodArgs.of("Validation will pass if username is null",
        bldr -> bldr.username(null), true));
    methods.add(MethodArgs.of("Validation will fail if username is non-null",
        bldr -> bldr.username(""), false));
    methods.add(MethodArgs.of("Validation will fail if email is non-null",
        bldr -> bldr.email("email@whatever.com"), false));

    return methods.stream()
        .map(args -> arguments(args.message(), args.builder(), args.isValid()));
  }

  private String convertCreateViolationsToString(
      Set<ConstraintViolation<BookingCreateRequest>> constraintViolations) {
    return constraintViolations
        .stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.joining(", "));
  }

  //endregion Helper Methods

  @TestConfiguration
  static class TestConfig {

    @Bean
    public ValidationContextProvider validationContextProvider() {
      return new ValidationContextProvider();
    }

    @Bean
    public Clock clock() {
      return clock;
    }

    @Bean
    public CustomFutureValidator customFutureValidator() {
      return new CustomFutureValidator();
    }

    @Bean
    public EnumValuesValidator enumValuesValidator() {
      return new EnumValuesValidator();
    }
  }
}

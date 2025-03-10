package piper1970.bookingservice.dto.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingCreateRequest.BookingCreateRequestBuilder;
import piper1970.bookingservice.dto.model.BookingUpdateRequest.BookingUpdateRequestBuilder;
import piper1970.bookingservice.validation.validators.EnumValuesValidator;
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;
import piper1970.eventservice.common.validation.validators.context.ValidationContextProvider;

@ExtendWith(SpringExtension.class)
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

  //region BookingUpdateRequest Validation Checks

  @DisplayName("BookingUpdateRequest validation assertions")
  @ParameterizedTest(name = "{index} ==> {0}")
  @MethodSource("namedUpdateRequestArguments")
  void testBookingUpdateRequest(String description,
      Consumer<BookingUpdateRequestBuilder> consumer, boolean isValid) {

    var bldr = goodBookingUpdateRequestBuilder();
    consumer.accept(bldr);
    var request = bldr.build();

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();

      var violations = validator.validate(request);

      System.out.println(convertUpdateViolationsToString(violations));

      assertThat(violations).isNotNull();

      if (isValid) {
        assertThat(violations).withFailMessage(description).isEmpty();
      } else {
        assertThat(violations).withFailMessage(description).isNotEmpty();
      }
    }
  }

  //endregion BookingUpdateRequest Validation Checks

  //region Helper Methods

  private BookingCreateRequest.BookingCreateRequestBuilder goodBookingCreateRequestBuilder() {
    return BookingCreateRequest.builder()
        .eventId(27)
        .username(null); // must be null to pass validation
  }

  private BookingUpdateRequest.BookingUpdateRequestBuilder goodBookingUpdateRequestBuilder() {
    return BookingUpdateRequest.builder()
        .eventDateTime(LocalDateTime.now(clock).plusDays(5))
        .bookingStatus(BookingStatus.IN_PROGRESS.name());
  }

  private static Stream<Arguments> namedUpdateRequestArguments() {

    record MethodArgs(String message, Consumer<BookingUpdateRequestBuilder> builder,
                      boolean isValid) {

      static MethodArgs of(String message, Consumer<BookingUpdateRequestBuilder> builder,
          boolean isValid) {
        return new MethodArgs(message, builder, isValid);
      }
    }

    List<MethodArgs> methods = new ArrayList<>();

    // validation for eventDateTime
    methods.add(MethodArgs.of("Should pass validation if eventDateTime is null",
        bldr -> bldr.eventDateTime(null), true));
    methods.add(MethodArgs.of("Should fail validation if eventDateTime is right now",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock)), false));
    methods.add(MethodArgs.of("Should pass validation if eventDateTime is in the future",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock).plusDays(1)), true));
    methods.add(MethodArgs.of("Should fail validation if eventDateTime is in the past",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock)
            .minusMinutes(1)), false));

    // validation for bookingStatus
    methods.add(MethodArgs.of("Should pass validation if bookingStatus is null",
        bldr -> bldr.bookingStatus(null), true));
    methods.add(MethodArgs.of("Should pass validation if bookingStatus is 'IN_PROGRESS'",
        bldr -> bldr.bookingStatus(BookingStatus.IN_PROGRESS.name()), true));
    methods.add(MethodArgs.of("Should pass validation if bookingStatus is 'CONFIRMED'",
        bldr -> bldr.bookingStatus(BookingStatus.CONFIRMED.name()), true));
    methods.add(MethodArgs.of("Should pass validation if bookingStatus is 'CANCELLED'",
        bldr -> bldr.bookingStatus(BookingStatus.CANCELLED.name()), true));
    methods.add(MethodArgs.of("Should fail validation if bookingStatus is blank",
        bldr -> bldr.bookingStatus(""), false));
    methods.add(MethodArgs.of("Should fail validation if bookingStatus is unrecognized value",
        bldr -> bldr.bookingStatus("NON_RECOGNIZED_STATUS"), false));

    return methods.stream()
        .map(args -> arguments(args.message(), args.builder(), args.isValid()));
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

  private String convertUpdateViolationsToString(
      Set<ConstraintViolation<BookingUpdateRequest>> constraintViolations) {
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

package piper1970.eventservice.service.dto.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.math.BigDecimal;
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
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;
import piper1970.eventservice.common.validation.validators.context.ValidationContextProvider;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventCreateRequest.EventCreateRequestBuilder;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest.EventUpdateRequestBuilder;

@ExtendWith(SpringExtension.class)
public class EventValidationTests {

  private static final Clock clock = Clock.systemDefaultZone();

  //region EventCreateRequest Validation Checks

  @DisplayName("EventCreateRequest validation assertions")
  @ParameterizedTest(name = "{index} ==> {0}")
  @MethodSource("namedCreateRequestArguments")
  void testEventCreateRequest(String message,
      Consumer<EventCreateRequestBuilder> consumer,
      boolean isValid) {

    var builder = goodEventCreateRequestBuilder();
    consumer.accept(builder);
    var result = builder.build();

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();

      var violations = validator.validate(result);

      System.out.println(convertCreateViolationsToString(violations));

      assertThat(violations).withFailMessage(message).isNotNull();

      if (isValid) {
        assertThat(violations).withFailMessage(message).isEmpty();
      } else {
        assertThat(violations).withFailMessage(message).isNotEmpty();
      }
    }

  }

  //endregion EventCreateRequest Validation Checks

  //region EventUpdateRequest Validation Checks

  @DisplayName("EventUpdateRequest validation assertions")
  @ParameterizedTest(name = "{index} ==> {0}")
  @MethodSource("namedUpdateRequestArguments")
  void testEventUpdateRequest(String message,
      Consumer<EventUpdateRequestBuilder> consumer,
      boolean isValid) {

    var builder = goodEventUpdateRequestBuilder();
    consumer.accept(builder);
    var request = builder.build();

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();

      var violations = validator.validate(request);

      System.out.println(convertUpdateViolationsToString(violations));

      assertThat(violations).withFailMessage(message).isNotNull();

      if (isValid) {
        assertThat(violations).withFailMessage(message).isEmpty();
      } else {
        assertThat(violations).withFailMessage(message).isNotEmpty();
      }
    }

  }

  //endregion EventUpdateRequest Validation Checks

  //region Helper Methods

  private EventCreateRequest.EventCreateRequestBuilder goodEventCreateRequestBuilder() {
    return EventCreateRequest.builder()
        .facilitator(null)
        .title("title")
        .description("description")
        .location("location")
        .eventDateTime(LocalDateTime.now(clock).plusHours(1))
        .durationInMinutes(30)
        .cost(BigDecimal.TEN)
        .availableBookings(50);
  }

  private EventUpdateRequest.EventUpdateRequestBuilder goodEventUpdateRequestBuilder() {
    return EventUpdateRequest.builder()
        .title("title")
        .description("description")
        .location("location")
        .eventDateTime(LocalDateTime.now(clock).plusHours(1))
        .durationInMinutes(30)
        .cost(BigDecimal.TEN)
        .availableBookings(50);
  }

  private String convertCreateViolationsToString(
      Set<ConstraintViolation<EventCreateRequest>> constraintViolations
  ) {
    return constraintViolations
        .stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.joining(", "));
  }

  private String convertUpdateViolationsToString(
      Set<ConstraintViolation<EventUpdateRequest>> constraintViolations
  ) {
    return constraintViolations
        .stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.joining(", "));
  }

  private static Stream<Arguments> namedCreateRequestArguments() {

    record MethodArgs(String message, Consumer<EventCreateRequestBuilder> builder,
                      boolean isValid) {

      static MethodArgs of(String message, Consumer<EventCreateRequestBuilder> builder,
          boolean isValid) {
        return new MethodArgs(message, builder, isValid);
      }
    }

    List<MethodArgs> methodArgs = new ArrayList<>();

    // facilitator checks
    methodArgs.add(MethodArgs.of("Should pass validation if facilitator is set to null",
        bldr -> bldr.facilitator(null), true));
    methodArgs.add(MethodArgs.of("Should fail validation if facilitator is not set to null",
        bldr -> bldr.facilitator("facilitator"), false));

    // title checks
    methodArgs.add(MethodArgs.of("Should fail validation if title is set to null",
        bldr -> bldr.title(null), false));
    methodArgs.add(MethodArgs.of("Should fail validation if title is blank",
        bldr -> bldr.title(""), false));
    methodArgs.add(MethodArgs.of("Should fail validation if title length exceeds 255 characters",
        bldr -> bldr.title("x".repeat(256)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if non-null title is less 255 characters or less",
        bldr -> bldr.title("x".repeat(255)), true));

    // description checks
    methodArgs.add(MethodArgs.of("Should pass validation if description is set to null",
        bldr -> bldr.description(null), true));
    methodArgs.add(MethodArgs.of("Should fail validation if description is blank",
        bldr -> bldr.description(""), false));
    methodArgs.add(MethodArgs.of("Should fail validation if description length exceeds 255 characters",
        bldr -> bldr.description("x".repeat(256)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if non-null description is less 255 characters or less",
        bldr -> bldr.description("x".repeat(255)), true));

    // location checks
    methodArgs.add(MethodArgs.of("Should fail validation if location is set to null",
        bldr -> bldr.location(null), false));
    methodArgs.add(MethodArgs.of("Should fail validation if location is blank",
        bldr -> bldr.location(""), false));
    methodArgs.add(MethodArgs.of("Should fail validation if location length exceeds 255 characters",
        bldr -> bldr.location("x".repeat(256)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if non-null location is less 255 characters or less",
        bldr -> bldr.location("x".repeat(255)), true));

    // eventDateTime checks
    methodArgs.add(MethodArgs.of("Should fail validation if eventDateTime is set to null",
        bldr -> bldr.eventDateTime(null), false));
    methodArgs.add(MethodArgs.of("Should fail validation if eventDateTime is set to current time",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock)), false));
    methodArgs.add(MethodArgs.of("Should fail validation if eventDateTime is set to past time",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock).minusMinutes(1)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if eventDateTime is set to future",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock).plusDays(1)), true));

    // durationInMinutes checks
    methodArgs.add(MethodArgs.of("Should fail validation if durationInMinutes is set to null",
        bldr -> bldr.durationInMinutes(null), false));
    methodArgs.add(MethodArgs.of("Should fail validation if durationInMinutes is under 30 minutes",
        bldr -> bldr.durationInMinutes(29), false));
    methodArgs.add(MethodArgs.of("Should pass validation if durationInMinutes is at least 30 minutes",
        bldr -> bldr.durationInMinutes(30), true));

    // cost checks
    methodArgs.add(MethodArgs.of("Should fail validation if cost is set to null",
        bldr -> bldr.cost(null), false));
    methodArgs.add(MethodArgs.of("Should fail validation if cost is less than 0",
        bldr -> bldr.cost(BigDecimal.valueOf(-0.1)), false));
    methodArgs.add(MethodArgs.of("Should fail validation if cost structure has too many decimals for money 'numeric(6,2)'",
        bldr -> bldr.cost(BigDecimal.valueOf(1.001)), false));
    methodArgs.add(MethodArgs.of("Should fail validation if cost structure has is too large in whole numbers 'numeric(6,2)'",
        bldr -> bldr.cost(BigDecimal.valueOf(10000.00)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if cost structure has is within database range 'numeric(6,2)'",
        bldr -> bldr.cost(BigDecimal.valueOf(1000.00)), true));

    // availableBookings checks
    methodArgs.add(MethodArgs.of("Should fail validation if availableBookings is set to null",
        bldr -> bldr.availableBookings(null), false));
    methodArgs.add(MethodArgs.of("Should fail validation if availableBookings is set too large (<=32767)",
        bldr -> bldr.availableBookings(32768), false));
    methodArgs.add(MethodArgs.of("Should fail validation if availableBookings is negative",
        bldr -> bldr.availableBookings(-1), false));
    methodArgs.add(MethodArgs.of("Should pass validation if availableBookings is 0",
        bldr -> bldr.availableBookings(0), true));
    methodArgs.add(MethodArgs.of("Should pass validation if availableBookings is greater than 0",
        bldr -> bldr.availableBookings(1), true));

    return methodArgs.stream()
        .map(args -> arguments(args.message(), args.builder(), args.isValid()));

  }

  private static Stream<Arguments> namedUpdateRequestArguments() {

    record MethodArgs(String message, Consumer<EventUpdateRequestBuilder> builder,
                      boolean isValid) {

      static MethodArgs of(String message, Consumer<EventUpdateRequestBuilder> builder,
          boolean isValid) {
        return new MethodArgs(message, builder, isValid);
      }
    }

    List<MethodArgs> methodArgs = new ArrayList<>();

    // title checks
    methodArgs.add(MethodArgs.of("Should pass validation if title is set to null",
        bldr -> bldr.title(null), true));
    methodArgs.add(MethodArgs.of("Should fail validation if title is blank",
        bldr -> bldr.title(""), false));
    methodArgs.add(MethodArgs.of("Should pass validation if title has text and is less or equal to 255 characters",
        bldr -> bldr.title("x".repeat(255)), true));
    methodArgs.add(MethodArgs.of("Should fail validation if title text is larger than 255 characters",
        bldr -> bldr.title("x".repeat(256)), false));

    // description checks
    methodArgs.add(MethodArgs.of("Should pass validation if description is set to null",
        bldr -> bldr.description(null), true));
    methodArgs.add(MethodArgs.of("Should fail validation if description is blank",
        bldr -> bldr.description(""), false));
    methodArgs.add(MethodArgs.of("Should pass validation if description has text and is less or equal to 255 characters",
        bldr -> bldr.description("x".repeat(255)), true));
    methodArgs.add(MethodArgs.of("Should fail validation if description text is larger than 255 characters",
        bldr -> bldr.description("x".repeat(256)), false));

    // location checks
    methodArgs.add(MethodArgs.of("Should pass validation if location is set to null",
        bldr -> bldr.location(null), true));
    methodArgs.add(MethodArgs.of("Should fail validation if location is blank",
        bldr -> bldr.location(""), false));
    methodArgs.add(MethodArgs.of("Should pass validation if location has text and is less or equal to 255 characters",
        bldr -> bldr.location("x".repeat(255)), true));
    methodArgs.add(MethodArgs.of("Should fail validation if location text is larger than 255 characters",
        bldr -> bldr.location("x".repeat(256)), false));

    // eventDateTime checks
    methodArgs.add(MethodArgs.of("Should fail validation if eventDateTime is set to current time",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock)), false));
    methodArgs.add(MethodArgs.of("Should fail validation if eventDateTime is set to past time",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock).minusMinutes(1)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if eventDateTime is set to future",
        bldr -> bldr.eventDateTime(LocalDateTime.now(clock).plusDays(1)), true));

    // duration checks
    methodArgs.add(MethodArgs.of("Should fail validation if durationInMinutes is under 30 minutes",
        bldr -> bldr.durationInMinutes(29), false));
    methodArgs.add(MethodArgs.of("Should pass validation if durationInMinutes is at least 30 minutes",
        bldr -> bldr.durationInMinutes(30), true));

    // cost checks
    methodArgs.add(MethodArgs.of("Should fail validation if cost is less than 0",
        bldr -> bldr.cost(BigDecimal.valueOf(-0.1)), false));
    methodArgs.add(MethodArgs.of("Should fail validation if cost structure has too many decimals for money 'numeric(6,2)'",
        bldr -> bldr.cost(BigDecimal.valueOf(1.001)), false));
    methodArgs.add(MethodArgs.of("Should fail validation if cost structure has is too large in whole numbers 'numeric(6,2)'",
        bldr -> bldr.cost(BigDecimal.valueOf(10000.00)), false));
    methodArgs.add(MethodArgs.of("Should pass validation if cost structure has is within database range 'numeric(6,2)'",
        bldr -> bldr.cost(BigDecimal.valueOf(1000.00)), true));

    // availableBookings checks
    methodArgs.add(MethodArgs.of("Should fail validation if availableBookings is set too large (<=32767)",
        bldr -> bldr.availableBookings(32768), false));
    methodArgs.add(MethodArgs.of("Should fail validation if availableBookings is negative",
        bldr -> bldr.availableBookings(-1), false));
    methodArgs.add(MethodArgs.of("Should pass validation if availableBookings is 0",
        bldr -> bldr.availableBookings(0), true));
    methodArgs.add(MethodArgs.of("Should pass validation if availableBookings is greater than zero",
        bldr -> bldr.availableBookings(1), true));

    return methodArgs.stream()
        .map(args -> arguments(args.message(), args.builder(), args.isValid()));

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
  }
}

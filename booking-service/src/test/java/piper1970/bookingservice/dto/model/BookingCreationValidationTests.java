package piper1970.bookingservice.dto.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import jakarta.validation.Validation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
@DisplayName("Booking Creation Validation Tests")
public class BookingCreationValidationTests {

  private static final Clock clock = Clock.systemDefaultZone();

  //region BookingCreateRequest Validation Checks

  /**
   * Main parameterized test to ensure all validation behavior works.
   *
   * @param description Message displayed if assertion fails
   * @param consumer BookingCreateRequestBuilder consumer to apply to BookingCreationRequest,
   *                 allowing fow individual field adjustment needed for tests
   * @param isValid flag to determine if test should or should not be considered valid
   *
   * @see #namedCreateRequestArguments
   */
  @DisplayName("should handle all validation assertions properly")
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

  /**
   * Helper method to generate passing BookingCreateRequest
   */
  private BookingCreateRequest.BookingCreateRequestBuilder goodBookingCreateRequestBuilder() {
    return BookingCreateRequest.builder()
        .eventId(27)
        .email(null) // must be null to pass validation
        .username(null); // must be null to pass validation
  }

  /**
   * Helper record for capturing necessary parameters for tests.
   * @param message Message with expected behavior
   * @param builder BookingCreateRequestBuilder consumer used to modify original BookingCreateRequestBuilder parameter
   * @param isValid flag to determine if validation should succeed or fail
   */
  record MethodArgs(String message, Consumer<BookingCreateRequestBuilder> builder,
                    boolean isValid) {

    /**
     * static builder function
     */
    static MethodArgs of(String message, Consumer<BookingCreateRequestBuilder> builder,
        boolean isValid) {
      return new MethodArgs(message, builder, isValid);
    }
  }

  /**
   * method for dynamically creating test arguments, based off of assertion message,
   * booking-create-request-builder consumer, and validity flag
   *
   * @see #testBookingCreateRequest
   */
  private static Stream<Arguments> namedCreateRequestArguments() {

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

    // return stream with MethodArgs converted to Arguments
    return methods.stream()
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

    @Bean
    public EnumValuesValidator enumValuesValidator() {
      return new EnumValuesValidator();
    }
  }
}

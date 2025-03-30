package piper1970.notificationservice.routehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("Booking Confirmation Handler")
@ExtendWith(MockitoExtension.class)
class BookingConfirmationHandlerTest {

  private BookingConfirmationHandler testHandler;

  //region Mocked Services

  @Mock
  private BookingConfirmationRepository mockRepository;

  @Mock
  private MessagePostingService mockPostingService;

  @Mock
  private ServerRequest mockServerRequest;

  //endregion Mocked Services

  //region Helper Fields

  private final Clock clock = Clock.systemUTC();
  private final Duration notificationTimeoutDuration = Duration.ofSeconds(5);

  private final UUID testToken = UUID.randomUUID();
  private final BookingConfirmation testBookingConfirmation = BookingConfirmation.
      builder()
      .confirmationString(testToken)
      .bookingId(1)
      .eventId(1)
      .confirmationDateTime(LocalDateTime.now(clock))
      .durationInMinutes(Math.toIntExact(notificationTimeoutDuration.toMinutes()))
      .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
      .bookingUser("test_user")
      .bookingEmail("test_user@test.com")
      .build();

  //endregion Helper Fields

  @BeforeEach
  void setUp() {

    ObjectMapper objectMapper = new ObjectMapper();

    testHandler = new BookingConfirmationHandler(
        mockRepository,
        mockPostingService,
        objectMapper,
        clock,
        notificationTimeoutDuration
    );
  }

  // @formatter:off

  /// SCENARIOS
  /// "confirmation token not uuid" -> BAD_REQUEST
  /// "timeout on finding by confirmation token" -> SERVICE_UNAVAILABLE
  /// "confirmation token not found" -> NOT_FOUND
  /// "confirmation-expired, but update times out"  -> SERVICE_UNAVAILABLE
  /// "confirmation-expires and update succeeds" -> BAD_REQUEST
  /// "confirmation-succeeds, but update times out" -> SERVICE_UNAVAILABLE
  /// "confirmation succeeds, and updated succeeds" -> OK

  // @formatter:on

  //region Tests
  @Test
  @DisplayName("should return BAD_REQUEST when the token string is not a valid UUID")
  void handleConfirmation__confirmation_token_not_uuid() {

    setupMockServerRequest("invalid_token");

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.BAD_REQUEST.value()))
        .verifyComplete();
  }

  @Test
  @DisplayName("should return SERVICE_UNAVAILABLE when attempt to find token times out")
  void handleConfirmation__find_times_out() {

    setupMockServerRequest(testToken.toString());

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(testBookingConfirmation)
                .delayElement(notificationTimeoutDuration)
        );

    StepVerifier.withVirtualTime(
            () -> testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration)
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.SERVICE_UNAVAILABLE.value())).verifyComplete();

  }

  @Test
  @DisplayName("should return NOT_FOUND when is not found")
  void handleConfirmation__confirmation_token_not_found() {

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(Mono.empty());

    setupMockServerRequest(testToken.toString());

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.NOT_FOUND.value()))
        .verifyComplete();
  }

  @Test
  @DisplayName("should return SERVICE_UNAVAILABLE when expired token is found, but cannot be saved due to timeout")
  void handleConfirmation__expired_token__update_times_out() {
    setupMockServerRequest(testToken.toString());

    var originalConfirmation = testBookingConfirmation.toBuilder()
        .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(70))
        .durationInMinutes(10)
        .build();

    var expiredConfirmation = originalConfirmation.toBuilder()
        .confirmationStatus(ConfirmationStatus.EXPIRED)
        .build();

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(originalConfirmation)
        );

    when(mockRepository.save(eq(expiredConfirmation)))
        .thenReturn(
            Mono.just(expiredConfirmation)
                .delayElement(notificationTimeoutDuration)
        );

    StepVerifier.withVirtualTime(
            () -> testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration)
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.SERVICE_UNAVAILABLE.value())).verifyComplete();
  }

  @Test
  @DisplayName("should return BAD_REQUEST when expired token is found and saved")
  void handleConfirmation__expired_token__update_success() {
    setupMockServerRequest(testToken.toString());

    var originalConfirmation = testBookingConfirmation.toBuilder()
        .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(70))
        .durationInMinutes(10)
        .build();

    var expiredConfirmation = originalConfirmation.toBuilder()
        .confirmationStatus(ConfirmationStatus.EXPIRED)
        .build();

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(originalConfirmation)
        );

    when(mockRepository.save(eq(expiredConfirmation)))
        .thenReturn(
            Mono.just(expiredConfirmation)
        );

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration)
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.BAD_REQUEST.value())).verifyComplete();
  }

  @Test
  @DisplayName("should return SERVICE_UNAVAILABLE when confirmed token is found, but cannot be saved due to timeout")
  void handleConfirmation__token_confirmed__update_times_out() {
    setupMockServerRequest(testToken.toString());

    var originalConfirmation = testBookingConfirmation.toBuilder()
        .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(10))
        .durationInMinutes(70)
        .build();

    var confirmedConfirmation = originalConfirmation.toBuilder()
        .confirmationStatus(ConfirmationStatus.CONFIRMED)
        .build();

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(originalConfirmation)
        );

    when(mockRepository.save(eq(confirmedConfirmation)))
        .thenReturn(
            Mono.just(confirmedConfirmation)
                .delayElement(notificationTimeoutDuration)
        );

    StepVerifier.withVirtualTime(
            () -> testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration)
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.SERVICE_UNAVAILABLE.value())).verifyComplete();
  }

  @Test
  @DisplayName("should return OK when confirmed token is found and saved")
  void handleConfirmation__token_confirmed__update_success() {
    setupMockServerRequest(testToken.toString());

    var originalConfirmation = testBookingConfirmation.toBuilder()
        .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(10))
        .durationInMinutes(70)
        .build();

    var confirmedConfirmation = originalConfirmation.toBuilder()
        .confirmationStatus(ConfirmationStatus.CONFIRMED)
        .build();

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(originalConfirmation)
        );

    when(mockRepository.save(eq(confirmedConfirmation)))
        .thenReturn(
            Mono.just(confirmedConfirmation)
        );

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration)
        .assertNext(serverResponse -> assertThat(serverResponse.statusCode().value()).isEqualTo(
            HttpStatus.OK.value())).verifyComplete();

  }

  //endregion Tests

  //region Helper Methods

  private void setupMockServerRequest(String message) {
    when(mockServerRequest.pathVariable(eq("confirmationString"))).thenReturn(message);
  }

  //endregion Helper Methods

}
package piper1970.notificationservice.routehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@DisplayName("Booking Confirmation Handler")
@ExtendWith(MockitoExtension.class)
@TestClassOrder(OrderAnnotation.class)
@Order(2)
class BookingConfirmationHandlerTests {


  //region Properties Used

  private BookingConfirmationHandler testHandler;

  @Mock
  private BookingConfirmationRepository mockRepository;

  @Mock
  private MessagePostingService mockPostingService;

  @Mock
  private Counter confirmationSuccessCounter;

  @Mock
  private Counter expiredCounter;

  @Mock
  private ServerRequest mockServerRequest;

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

  private final Retry retry = Retry.backoff(3, Duration.ofMillis(500))
      .filter(throwable -> throwable instanceof TimeoutException)
      .jitter(0.7D);

  //endregion Properties Used

  //region Before/After

  @BeforeEach
  void setUp() {
    long maxRetries = 2;
    testHandler = new BookingConfirmationHandler(
        mockRepository,
        mockPostingService,
        new ObjectMapper(),
        confirmationSuccessCounter,
        expiredCounter,
        clock,
        notificationTimeoutDuration,
        maxRetries,
        retry
    );
  }

  //endregion Before/After

  // @formatter:off

  /// SCENARIOS
  /// "confirmation token not uuid" -> BAD_REQUEST
  /// "timeout on finding by confirmation token" -> SERVICE_UNAVAILABLE
  /// "confirmation token not found" -> NOT_FOUND
  /// "confirmation-expired, but update times out"  -> SERVICE_UNAVAILABLE
  /// "confirmation-expires and update succeeds" -> BAD_REQUEST
  /// "confirmation-succeeds, but update times out" -> SERVICE_UNAVAILABLE
  /// "confirmation succeeds, and updated succeeds" -> OK
  /// "re-click on succeeded confirmation link -> NOT_FOUND
  /// "re-click on expired confirmation -> NOT_FOUND

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
                .delayElement(notificationTimeoutDuration) // create artificial time delay
        );

    StepVerifier.withVirtualTime(
            () -> testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration.multipliedBy(10))
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
                .delayElement(notificationTimeoutDuration) // create artificial time delay
        );

    StepVerifier.withVirtualTime(
            () -> testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration.multipliedBy(10))
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

    when(mockPostingService.postBookingExpiredMessage(any(BookingExpired.class)))
            .thenReturn(Mono.empty());

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
        .assertNext(serverResponse -> {
          assertThat(serverResponse.statusCode().value()).isEqualTo(
              HttpStatus.BAD_REQUEST.value());
          verify(mockPostingService, times(1)).postBookingExpiredMessage(any(BookingExpired.class));
        }).verifyComplete();
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
                .delayElement(notificationTimeoutDuration) // artificial delay
        );

    StepVerifier.withVirtualTime(
            () -> testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration.multipliedBy(10))
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

    when(mockPostingService.postBookingConfirmedMessage(any(BookingConfirmed.class)))
        .thenReturn(Mono.empty());

    when(mockRepository.save(eq(confirmedConfirmation)))
        .thenReturn(
            Mono.just(confirmedConfirmation)
        );

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .expectSubscription()
        .thenAwait(notificationTimeoutDuration)
        .assertNext(serverResponse -> {
          assertThat(serverResponse.statusCode().value()).isEqualTo(
              HttpStatus.OK.value());
          verify(mockPostingService, times(1)).postBookingConfirmedMessage(any(BookingConfirmed.class));
        }).verifyComplete();

  }

  @Test
  @DisplayName("should return NOT_FOUND when confirmed token link re-clicked")
  void handleConfirmation__reclick_on_confirmed_link__not_found() {
    setupMockServerRequest(testToken.toString());

    var clickedConfirmation = testBookingConfirmation.toBuilder()
        .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(10))
        .durationInMinutes(70)
        .confirmationStatus(ConfirmationStatus.CONFIRMED)
        .build();

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(clickedConfirmation)
        );

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .assertNext(serverResponse -> {
          assertThat(serverResponse.statusCode().value()).isEqualTo(
              HttpStatus.NOT_FOUND.value());
          verify(mockPostingService, never()).postBookingConfirmedMessage(any(BookingConfirmed.class));
        }).verifyComplete();
  }

  @Test
  @DisplayName("should return NOT_FOUND when expired token link re-clicked")
  void handleConfirmation__reclick_on_expired_link__not_found() {
    setupMockServerRequest(testToken.toString());

    var clickedConfirmation = testBookingConfirmation.toBuilder()
        .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(10))
        .durationInMinutes(70)
        .confirmationStatus(ConfirmationStatus.EXPIRED)
        .build();

    when(mockRepository.findByConfirmationString(eq(testToken)))
        .thenReturn(
            Mono.just(clickedConfirmation)
        );

    StepVerifier.create(testHandler.handleConfirmation(mockServerRequest))
        .assertNext(serverResponse -> {
          assertThat(serverResponse.statusCode().value()).isEqualTo(
              HttpStatus.NOT_FOUND.value());
          verify(mockPostingService, never()).postBookingConfirmedMessage(any(BookingConfirmed.class));
        }).verifyComplete();
  }

  //endregion Tests

  //region Helper Methods

  private void setupMockServerRequest(String message) {
    when(mockServerRequest.pathVariable(eq("confirmationString"))).thenReturn(message);
  }

  //endregion Helper Methods

}
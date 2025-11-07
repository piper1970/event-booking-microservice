package piper1970.bookingservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.Nullable;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.mapper.BookingMapper;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.exceptions.BookingCancellationException;
import piper1970.bookingservice.exceptions.BookingCreationException;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.exceptions.BookingTimeoutException;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@ExtendWith(MockitoExtension.class)
@DisplayName("Booking Web Service")
@TestClassOrder(OrderAnnotation.class)
@Order(3)
class DefaultBookingWebServiceTests {

  // service to test
  private DefaultBookingWebService webService;

  // mocked services
  @Mock
  BookingRepository bookingRepository;
  @Mock
  EventRequestService eventRequestService;
  @Mock
  MessagePostingService messagePostingService;
  @Mock
  BookingMapper bookingMapper;
  @Mock
  TransactionalOperator transactionalOperator;
  @Mock
  Clock clock;

  // values for mocked clock
  final Instant clockInstant = Instant.now();
  final ZoneId clockZone = ZoneId.systemDefault();

  // common variables used for tests
  private static final String token = "Eat at Al's";
  private static final String username = "test_user";
  private static final int eventId = 27;
  private static final int bookingId = 1;
  private static final int allBookingsCount = 3;
  private static final Long timeoutValue = 2000L;
  private static final Duration timeoutDuration = Duration.ofMillis(timeoutValue);
  private static final String errorMessage = "Something went wrong";
  private static final Retry defaultRepository = Retry.backoff(3, Duration.ofMillis(500L))
      .filter(throwable -> throwable instanceof TimeoutException)
      .jitter(0.7D);
  private static final Retry defaultKafkaRetry = Retry.backoff(3, Duration.ofMillis(500L))
      .filter(throwable -> throwable instanceof TimeoutException)
      .jitter(0.7D);

  @BeforeEach
  void setUp() {
    webService = new DefaultBookingWebService(
        bookingMapper,
        bookingRepository,
        eventRequestService,
        messagePostingService,
        transactionalOperator,
        timeoutValue,
        defaultRepository,
        defaultKafkaRetry
    );
  }

  //region Find Method Scenarios

  ///  # FIND SCENARIOS
  ///  - I've found you - returns booking
  ///  - I haven't found you - returns nothing but no errors
  ///  - I've timed out -> throws BookingTimeoutException
  ///
  ///   _Same pattern for all find methods_

  @Test
  @DisplayName("findAllBookings should be able to retrieve all bookings")
  void findAllBookings() {

    mockBookingMapper();

    when(bookingRepository.findAll()).thenReturn(createBookingFlux(null));

    StepVerifier.create(webService.findAllBookings())
        .expectNextCount(allBookingsCount)
        .verifyComplete();
  }

  @Test
  @DisplayName("findAllBookings should not error out if no bookings are fond")
  void findAllBookings_NoResponse() {

    when(bookingRepository.findAll()).thenReturn(Flux.empty());

    StepVerifier.create(webService.findAllBookings())
        .verifyComplete();
  }

  @Test
  @DisplayName("findAllBookings should throw proper exception if it takes too long")
  void findAllBookings_TimeoutExceeded() {

    when(bookingRepository.findAll()).thenReturn(createBookingFlux(null)
        .delaySequence(timeoutDuration)
    );

    StepVerifier.withVirtualTime(() -> webService.findAllBookings())
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("findBookingsByUsername should return bookings by user if in the system")
  void findBookingsByUsername_UserFound() {

    mockBookingMapper();

    var expectedCount = createBookingStream(username)
        .filter(booking -> booking.getUsername().equals(username))
        .count();

    when(bookingRepository.findByUsername(username)).thenReturn(createBookingFlux(username)
        .filter(booking -> booking.getUsername().equals(username))
    );

    StepVerifier.create(webService.findBookingsByUsername(username))
        .expectNextCount(expectedCount)
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingsByUsername should not error out if bookings for username are not in the system")
  void findBookingsByUsername_UserNotFound() {

    when(bookingRepository.findByUsername(username)).thenReturn(createBookingFlux(null)
        .filter(booking -> booking.getUsername().equals(username))
    );

    StepVerifier.create(webService.findBookingsByUsername(username))
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingsByUsername should throw error if booking-repo takes too long")
  void findBookingsByUsername_TimedOut() {

    when(bookingRepository.findByUsername(username)).thenReturn(createBookingFlux(username)
        .delaySequence(timeoutDuration)
        .filter(booking -> booking.getUsername().equals(username))
    );

    StepVerifier.withVirtualTime(() -> webService.findBookingsByUsername(username))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("findBookingById should properly return booking found with id")
  void findBookingById() {

    mockBookingMapper();

    var bookingParams = BookingParams.of(bookingId, eventId);
    var booking = createBooking(bookingParams);
    var bookingDto = createBookingDto(bookingParams);

    when(bookingRepository.findById(bookingId)).thenReturn(Mono.just(booking));

    StepVerifier.create(webService.findBookingById(bookingId))
        .expectNext(bookingDto)
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingById should not error out if booking with given id is not in the system")
  void findBookingById_NotFound() {

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.empty());

    StepVerifier.create(webService.findBookingById(bookingId))
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingById should throw error if booking-repo takes too long")
  void findBookingById_TimedOut() {

    var booking = createBooking(BookingParams.of(bookingId, eventId));

    when(bookingRepository.findById(bookingId)).thenReturn(Mono.just(booking)
        .delayElement(timeoutDuration)
    );

    StepVerifier.withVirtualTime(() -> webService.findBookingById(bookingId))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);

  }

  @Test
  @DisplayName("findBookingByIdAndUsername should properly return booking with given user and id")
  void findBookingByIdAndUsername_BookingFound() {

    mockBookingMapper();

    var params = BookingParams.of(bookingId, eventId, username);
    var booking = createBooking(params);
    var bookingDto = createBookingDto(params);

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    StepVerifier.create(webService.findBookingByIdAndUsername(bookingId, username))
        .expectNext(bookingDto)
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingByIdAndUsername should throw error if booking with given user and id cannot be found")
  void findBookingByIdAndUsername_BookingNotFound() {

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.empty());

    StepVerifier.create(webService.findBookingByIdAndUsername(bookingId, username))
        .verifyError(BookingNotFoundException.class);

  }

  @Test
  @DisplayName("findBookingByIdAndUsername should throw error if it takes too long")
  void findBookingByIdAndUsername_TimedOut() {

    var params = BookingParams.of(bookingId, eventId, username);
    var booking = createBooking(params);

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking)
            .delayElement(timeoutDuration)
        );

    StepVerifier.withVirtualTime(() -> webService.findBookingByIdAndUsername(bookingId, username))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);
  }

  //endregion Find Method Scenarios

  //region Create Method Scenarios

  ///  ## CREATE SCENARIOS
  /// - call to event-request-service returns error -> error passes through directly to caller
  /// - validation fails (event for booking must be in AWAITING STATE) -> throws
  /// BookingCreationException
  /// - repo call to save times out -> throws BookingTimeoutException
  /// - call to save works as expected -> returns Mono[Void] response

  @Test
  @DisplayName("createBooking should pass exception through when thrown by event-request-service")
  void createBooking_throws_exception_if_event_request_service_throws_error() {

    var cbr = createBookingRequest();

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.error(new RuntimeException(errorMessage)));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.createBooking(cbr, token))
        .verifyError(RuntimeException.class);
  }

  @Test
  @DisplayName("createBooking should throw exception if event is already in progress")
  void createBooking_throws_exception_if_validation_fails_event_is_already_in_progress() {

    mockClock();

    var cbr = createBookingRequest();
    var eventDto = buildEventDto(LocalDateTime.now(clock).minusMinutes(10), 120);

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.createBooking(cbr, token))
        .verifyError(BookingCreationException.class);
  }

  @Test
  @DisplayName("createBooking should throw exception if event is over")
  void createBooking_throws_exception_if_validation_fails_event_over() {

    mockClock();

    var cbr = createBookingRequest();
    var eventDto = buildEventDto(LocalDateTime.now(clock).minusDays(10), 10);

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.createBooking(cbr, token))
        .verifyError(BookingCreationException.class);
  }

  @Test
  @DisplayName("createBooking should throw exception if attempt to save booking to database times out")
  void createBooking_throws_exception_if_call_to_save_to_database_times_out() {

    mockClock();

    var cbr = createBookingRequest();
    var eventDto = buildEventDto(LocalDateTime.now(clock).plusDays(10), 12);
    var booking = createBooking(BookingParams.of(bookingId, eventId, username));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(bookingRepository.save(any(Booking.class))).thenReturn(Mono.just(booking)
        .delayElement(timeoutDuration)
    );

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.withVirtualTime(() -> webService.createBooking(cbr, token))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("createBooking should should return a Mono<Void> response upon success")
  void createBooking_success() {

    mockClock();
    mockBookingMapper();

    var cbr = createBookingRequest();
    var eventDto = buildEventDto(LocalDateTime.now(clock).plusDays(10), 120);
    var params = BookingParams.of(bookingId, eventId, username);
    var booking = createBooking(params);
    var bookingDto = createBookingDto(params);

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(bookingRepository.save(any(Booking.class))).thenReturn(Mono.just(booking));

    when(messagePostingService.postBookingCreatedMessage(any(BookingCreated.class)))
        .thenReturn(Mono.empty());

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.createBooking(cbr, token))
        .expectNext(bookingDto)
        .verifyComplete();
  }

  //endregion Create Method Scenarios

  //region Cancel Method Scenarios

  /// ## CANCEL SCENARIOS
  /// - can't find booking -> throws BookingNotFoundException
  /// - timeout when trying to find booking -> throws BookingTimeoutException
  /// - validation failure - event still in progress -> throws BookingCancellationException
  /// - validation failure - booking already cancelled -> throws BookingCancellationException
  /// - call to event-request-service returns error -> error passes through to caller
  /// - timeout when trying to delete booking -> throws BookingTimeoutException
  /// - successfully found and delete booking without failing validations -> returns void

  @Test
  @DisplayName("cancelBooking should throw BookingNotFoundException if it can't be found")
  void cancelBooking_CantFindBooking() {

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.empty());

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.cancelBooking(bookingId, username, token))
        .verifyError(BookingNotFoundException.class);
  }

  @Test
  @DisplayName("cancelBooking should throw BookingTimeoutException if the repository times out when looking up the booking")
  void cancelBooking_timeout_while_trying_to_find_booking() {

    var booking = createBooking(BookingParams.of(bookingId, eventId, token));

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking)
            .delayElement(timeoutDuration));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.withVirtualTime(() -> webService.cancelBooking(bookingId, username, token))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("cancelBooking should throw BookingCancellationException if the booking event is in progress")
  void cancelBooking_validation_fails_event_still_in_progress() {

    mockClock();

    var booking = createBooking(BookingParams.of(bookingId, eventId, token));
    var event = buildEventDto(LocalDateTime.now(clock).minusDays(10), 80);

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(event));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.cancelBooking(bookingId, username, token))
        .verifyError(BookingCancellationException.class);
  }

  @Test
  @DisplayName("cancelBooking should throw BookingCancellationException if the booking event is in progress")
  void cancelBooking_validation_fails_booking_already_cancelled() {

    var booking = createBooking(BookingParams.of(bookingId, eventId, token))
        .withBookingStatus(BookingStatus.CANCELLED);

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.cancelBooking(bookingId, username, token))
        .verifyError(BookingCancellationException.class);
  }

  @Test
  @DisplayName("cancelBooking should pass error through if the event-request-services throws an error")
  void cancelBooking_call_to_event_request_service_times_out() {

    var booking = createBooking(BookingParams.of(bookingId, eventId, token));

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.error(new RuntimeException(errorMessage)));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.cancelBooking(bookingId, username, token))
        .verifyError(RuntimeException.class);
  }

  @Test
  @DisplayName("cancelBooking should throw BookingTimeoutException if the repo call to delete takes too long...")
  void cancelBooking_timeout_while_trying_to_cancel_booking() {

    mockClock();

    var booking = createBooking(BookingParams.of(bookingId, eventId, token));
    var cancelledBooking = booking.withBookingStatus(BookingStatus.CANCELLED);
    var event = buildEventDto(LocalDateTime.now(clock).plusHours(10), 80);

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(event));

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    when(bookingRepository.save(cancelledBooking))
    .thenReturn(Mono.just(cancelledBooking)
    .delayElement(timeoutDuration));

    StepVerifier.withVirtualTime(() -> webService.cancelBooking(bookingId, username, token))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("cancelBooking should return updated booking if everything goes as planned")
  void cancelBooking_Success_Returns_Cancelled_Booking() {

    mockClock();
    mockBookingMapper();

    var booking = createBooking(BookingParams.of(bookingId, eventId, token));
    var cancelledBooking = booking.withBookingStatus(BookingStatus.CANCELLED);
    var event = buildEventDto(LocalDateTime.now(clock).minusDays(10), 80)
        .withEventStatus(EventStatus.AWAITING);

    when(bookingRepository.findByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(event));

    when(bookingRepository.save(cancelledBooking))
    .thenReturn(Mono.just(cancelledBooking));

    when(messagePostingService.postBookingCancelledMessage(any(BookingCancelled.class)))
        .thenReturn(Mono.empty());

    when(transactionalOperator.transactional(ArgumentMatchers.<Mono<BookingDto>>any())).thenAnswer(args -> args.getArgument(0));

    StepVerifier.create(webService.cancelBooking(bookingId, username, token))
        .expectNextCount(1)
        .verifyComplete();
  }

  //endregion Cancel Method Scenarios

  //region Helper Methods

  private EventDto buildEventDto(LocalDateTime eventDateTime, Integer duration) {

    // set event-status based on comparison of now, eventDateTime and duration
    // assume cancelled is not an option
    var now = LocalDateTime.now(clock);
    var endTime = eventDateTime.plusMinutes(duration);
    EventStatus status;

    if(now.isAfter(endTime)) {
      status = EventStatus.COMPLETED;
    }else if(now.isAfter(eventDateTime)) {
      status = EventStatus.IN_PROGRESS;
    }else{
      status = EventStatus.AWAITING;
    }

    return EventDto.builder()
        .id(eventId)
        .title("title")
        .description("description")
        .location("location")
        .facilitator("facilitator")
        .availableBookings(10)
        .eventDateTime(eventDateTime)
        .durationInMinutes(duration)
        .eventStatus(status)
        .build();
  }

  private BookingCreateRequest createBookingRequest() {
    return BookingCreateRequest.builder()
        .eventId(eventId)
        .username(username)
        .build();
  }

  private Flux<Booking> createBookingFlux(@Nullable String testUser) {
    return Flux.fromStream(createBookingStream(testUser));
  }

  private Stream<Booking> createBookingStream(@Nullable String testUser) {
    return IntStream.range(0, allBookingsCount)
        .mapToObj(id -> {
          if (id % 2 == 0) {
            return BookingParams.of(bookingId + id, eventId + id, testUser);
          } else {
            return BookingParams.of(bookingId + id, eventId + id);
          }
        })
        .map(this::createBooking);
  }

  private record BookingParams(int id, int eventId, @Nullable String user) {

    static BookingParams of(int id, int eventId) {
      return new BookingParams(id, eventId, null);
    }

    static BookingParams of(int id, int eventId, @Nullable String user) {
      return new BookingParams(id, eventId, user);
    }
  }

  private BookingDto createBookingDto(BookingParams bookingParams) {

    var user = bookingParams.user() == null ? "User-" + username : bookingParams.user();

    return BookingDto.builder()
        .id(bookingParams.id())
        .eventId(bookingParams.eventId())
        .username(user)
        .bookingStatus(BookingStatus.IN_PROGRESS.name())
        .build();
  }

  private Booking createBooking(BookingParams bookingParams) {

    var user = bookingParams.user() == null ? "User-" + username : bookingParams.user();

    return Booking.builder()
        .id(bookingParams.id())
        .eventId(bookingParams.eventId())
        .username(user)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();
  }

  /**
   * Helper method to optionally mock booking mapper behavior
   */
  private void mockBookingMapper() {
    when(bookingMapper.entityToDto(any())).thenAnswer(
        args -> {
          Booking booking = args.getArgument(0);
          return BookingDto.builder()
              .id(booking.getId())
              .username(booking.getUsername())
              .eventId(booking.getEventId())
              .bookingStatus(booking.getBookingStatus().name())
              .build();
        }
    );
  }

  /**
   * Helper method to optionally mock clock behavior
   */
  private void mockClock() {
    given(clock.instant()).willReturn(clockInstant);
    given(clock.getZone()).willReturn(clockZone);
  }

  //endregion Helper Methods

}
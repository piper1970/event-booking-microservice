package piper1970.bookingservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.Nullable;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.exceptions.BookingCancellationException;
import piper1970.bookingservice.exceptions.BookingCreationException;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.exceptions.BookingTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceTimeoutException;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("Booking Web Service")
class DefaultBookingWebServiceTest {

  private DefaultBookingWebService webService;

  private static final int allBookingsCount = 3;

  private static final Long timeoutValue = 2000L;

  private static final String token = "Eat at Al's";
  private static final String username = "test_user";
  private static final int eventId = 27;

  @Mock
  BookingRepository bookingRepository;

  @Mock
  EventRequestService eventRequestService;

  @Mock
  EventDtoToStatusMapper eventDtoToStatusMapper;

  @BeforeEach
  void setUp() {
    webService = new DefaultBookingWebService(
        bookingRepository,
        eventRequestService,
        eventDtoToStatusMapper,
        timeoutValue
    );
  }

  //region Find Method Scenarios

  ///  ## FIND SCENARIOS
  ///  - I've found you - returns booking
  ///  - I haven't found you - returns nothing but no errors
  ///  - I've timed out -> throws BookingTimeoutException
  ///
  ///   _Same pattern for all find methods_

  @Test
  @DisplayName("findAllBookings should be able to retrieve all bookings")
  void findAllBookings() {

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
        .delaySequence(Duration.ofSeconds(10))
    );

    StepVerifier.withVirtualTime(() -> webService.findAllBookings())
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);

  }

  @Test
  @DisplayName("findBookingsByUsername should return bookings by user if in the system")
  void findBookingsByUsername_UserFound() {
    var username = "test_user";

    when(bookingRepository.findByUsername(username)).thenReturn(createBookingFlux(username)
        .filter(booking -> booking.getUsername().equals(username))
    );

    var expectedCount = createBookingStream(username)
        .filter(booking -> booking.getUsername().equals(username))
        .count();

    StepVerifier.create(webService.findBookingsByUsername(username))
        .expectNextCount(expectedCount)
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingsByUsername should not error out if bookings for username are not in the system")
  void findBookingsByUsername_UserNotFound() {
    var username = "test_user";

    when(bookingRepository.findByUsername(username)).thenReturn(createBookingFlux(null)
        .filter(booking -> booking.getUsername().equals(username))
    );

    StepVerifier.create(webService.findBookingsByUsername(username))
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingsByUsername should throw error if booking-repo takes too long")
  void findBookingsByUsername_TimedOut() {
    var username = "test_user";

    when(bookingRepository.findByUsername(username)).thenReturn(createBookingFlux(username)
        .delaySequence(Duration.ofSeconds(10))
        .filter(booking -> booking.getUsername().equals(username))
    );

    StepVerifier.withVirtualTime(() -> webService.findBookingsByUsername(username))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("findBookingById should properly return booking found with id")
  void findBookingById() {
    var id = 97;
    var booking = createBooking(new UserIdPair(id, null));

    when(bookingRepository.findById(id)).thenReturn(Mono.just(booking));

    StepVerifier.create(webService.findBookingById(id))
        .expectNext(booking)
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingById should not error out if booking with given id is not in the system")
  void findBookingById_NotFound() {
    var bookingId = 2;

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.empty());

    StepVerifier.create(webService.findBookingById(bookingId))
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingById should throw error if booking-repo takes too long")
  void findBookingById_TimedOut() {
    var id = 97;
    var booking = createBooking(new UserIdPair(id, null));

    when(bookingRepository.findById(id)).thenReturn(Mono.just(booking)
        .delayElement(Duration.ofSeconds(10))
    );

    StepVerifier.withVirtualTime(() -> webService.findBookingById(id))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);

  }

  @Test
  @DisplayName("findBookingByIdAndUsername should properly return booking with given user and id")
  void findBookingByIdAndUsername_BookingFound() {
    var bookingId = 2;
    var username = "test_user";
    var booking = createBooking(new UserIdPair(bookingId, username));

    when(bookingRepository.findBookingByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking));

    StepVerifier.create(webService.findBookingByIdAndUsername(bookingId, username))
        .expectNext(booking)
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingByIdAndUsername should not throw error if booking with given user and id cannot be found")
  void findBookingByIdAndUsername_BookingNotFound() {
    var bookingId = 2;
    var username = "test_user";

    when(bookingRepository.findBookingByIdAndUsername(bookingId, username))
        .thenReturn(Mono.empty());

    StepVerifier.create(webService.findBookingByIdAndUsername(bookingId, username))
        .verifyComplete();
  }

  @Test
  @DisplayName("findBookingByIdAndUsername should throw error if it takes too long")
  void findBookingByIdAndUsername_TimedOut() {
    var bookingId = 2;
    var username = "test_user";
    var booking = createBooking(new UserIdPair(bookingId, username));

    when(bookingRepository.findBookingByIdAndUsername(bookingId, username))
        .thenReturn(Mono.just(booking)
            .delayElement(Duration.ofSeconds(10))
        );

    StepVerifier.withVirtualTime(() -> webService.findBookingByIdAndUsername(bookingId, username))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);
  }

  //endregion Find Method Scenarios

  //region CREATE Method Scenarios

  ///  ## CREATE SCENARIOS
  /// - call to event-request-service times out -> throws EventRequestServiceTimeoutException
  /// - validation fails (event for booking must be in AWAITING STATE) -> throws BookingCreationException
  /// - repo call to save times out -> throws BookingTimeoutException
  /// - call to save works as expected -> returns Mono[Void] response
  ///
  /// ___invalid token... how will event-request-service behave if token is invalid?___

  @Test
  @DisplayName("createBooking should throw exception if the event-request-service times out")
  void createBooking_throws_exception_if_event_request_times_out() {
    var cbr = createBookingRequest();

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.error(new EventRequestServiceTimeoutException("oops")));

    StepVerifier.create(webService.createBooking(cbr, token))
        .verifyError(EventRequestServiceTimeoutException.class);
  }

  @Test
  @DisplayName("createBooking should throw exception if event is already in progress")
  void createBooking_throws_exception_if_validation_fails_event_is_already_in_progress() {
    var cbr = createBookingRequest();

    var eventDto = buildEventDto(LocalDateTime.now().minusMinutes(10), 120);

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.IN_PROGRESS);

    StepVerifier.create(webService.createBooking(cbr, token))
        .verifyError(BookingCreationException.class);
  }

  @Test
  @DisplayName("createBooking should throw exception if event is over")
  void createBooking_throws_exception_if_validation_fails_event_over() {
    var cbr = createBookingRequest();

    var eventDto = buildEventDto(LocalDateTime.now().minusDays(10), 10);

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.COMPLETED);

    StepVerifier.create(webService.createBooking(cbr, token))
        .verifyError(BookingCreationException.class);
  }

  @Test
  @DisplayName("createBooking should throw exception if attempt to save booking to database times out")
  void createBooking_throws_exception_if_call_to_save_to_database_times_out() {
    var cbr = createBookingRequest();

    var eventDto = buildEventDto(LocalDateTime.now().plusDays(10), 12);

    var booking = createBooking(new UserIdPair(eventId, username));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.AWAITING);

    when(bookingRepository.save(any(Booking.class))).thenReturn(Mono.just(booking)
        .delayElement(Duration.ofSeconds(10))
    );

    StepVerifier.withVirtualTime(() -> webService.createBooking(cbr, token))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("createBooking should should return a Mono<Void> response upon success")
  void createBooking_success() {
    var cbr = createBookingRequest();

    var eventDto = buildEventDto(LocalDateTime.now().plusDays(10), 120);

    var booking = createBooking(new UserIdPair(eventId, username));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(eventDto));

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.AWAITING);

    when(bookingRepository.save(any(Booking.class))).thenReturn(Mono.just(booking));

    StepVerifier.create(webService.createBooking(cbr, token))
        .expectNext(booking)
        .verifyComplete();
  }

  //endregion CREATE Method Scenarios

  //region Delete Method Scenarios

  /// ## DELETE SCENARIOS
  /// - can't find booking -> throws BookingNotFoundException
  /// - timeout when trying to find booking -> throws BookingTimeoutException
  /// - validation failure - event still in progress -> throws BookingCancellationException
  /// - call to event-request-service times out -> throws EventRequestServiceTimeoutException
  /// - timeout when trying to delete booking -> throws BookingTimeoutException
  /// - successfully found and delete booking without failing validations -> returns void
  ///
  /// ___invalid token... how will event-request-service behave if token is invalid?___

  @Test
  @DisplayName("deleteBooking should throw BookingNotFoundException if it can't be found")
  void deleteBooking_CantFindBooking() {
    var bookingId = 2;
    var token = "I'm a little teapot...";

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.empty());

    // no other mocking needed here, since first call above should trip exception...

    StepVerifier.create(webService.deleteBooking(bookingId, token))
        .verifyError(BookingNotFoundException.class);
  }

  @Test
  @DisplayName("deleteBooking should throw BookingTimeoutException if the repository times out when looking up the booking")
  void deleteBooking_timeout_while_trying_to_find_booking() {
    var bookingId = 2;
    var token = "I'm a little teapot...";
    var booking = createBooking(new UserIdPair(bookingId, token));

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.just(booking)
            .delayElement(Duration.ofSeconds(10)));

    StepVerifier.withVirtualTime(() -> webService.deleteBooking(bookingId, token))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("deleteBooking should throw BookingCancellationException if the booking event is in progress")
  void deleteBooking_validation_fails_event_still_in_progress() {
    var bookingId = 2;
    var token = "I'm a little teapot...";
    var booking = createBooking(new UserIdPair(bookingId, token));
    var eventId = booking.getEventId();
    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.just(booking));

    var event = EventDto.builder()
        .id(eventId)
        .title("title")
        .description("description")
        .location("location")
        .cost(BigDecimal.TEN)
        .facilitator("facilitator")
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().minusMinutes(10))  // IN PROGRESS
        .durationInMinutes(80)
        .build();
    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(event));

    when(eventDtoToStatusMapper.apply(event)).thenReturn(EventStatus.IN_PROGRESS);

    StepVerifier.create(webService.deleteBooking(bookingId, token))
        .verifyError(BookingCancellationException.class);
  }

  @Test
  @DisplayName("deleteBooking should throw EventRequestServiceTimeoutException if the event-request-services throws..")
  void deleteBooking_call_to_event_request_service_times_out() {
    var bookingId = 2;
    var token = "I'm a little teapot...";
    var booking = createBooking(new UserIdPair(bookingId, token));
    var eventId = booking.getEventId();

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.just(booking));

    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.error(new EventRequestServiceTimeoutException("oops")));

    StepVerifier.create(webService.deleteBooking(bookingId, token))
        .verifyError(EventRequestServiceTimeoutException.class);
  }

  @Test
  @DisplayName("deleteBooking should throw BookingTimeoutException if the repo call to delete takes too long...")
  void deleteBooking_timeout_while_trying_to_delete_booking() {
    var bookingId = 2;
    var token = "I'm a little teapot...";
    var booking = createBooking(new UserIdPair(bookingId, token));
    var eventId = booking.getEventId();

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.just(booking));

    var event = EventDto.builder()
        .id(eventId)
        .title("title")
        .description("description")
        .location("location")
        .cost(BigDecimal.TEN)
        .facilitator("facilitator")
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusHours(10))  // AWAITING
        .durationInMinutes(80)
        .build();
    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(event));

    when(eventDtoToStatusMapper.apply(event)).thenReturn(EventStatus.AWAITING);

    when(bookingRepository.deleteById(bookingId))
        .thenReturn(Mono.just(mock(Void.class)) // mock void best to be done, since mono.empty can't be delayed...
            .delayElement(Duration.ofSeconds(10)));

    StepVerifier.withVirtualTime(() -> webService.deleteBooking(bookingId, token))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(11))
        .verifyError(BookingTimeoutException.class);
  }

  @Test
  @DisplayName("deleteBooking should return void if everything goes as planned")
  void deleteBooking_Success_Returns_Void() {
    var bookingId = 2;
    var token = "I'm a little teapot...";
    var booking = createBooking(new UserIdPair(bookingId, token));
    var eventId = booking.getEventId();

    when(bookingRepository.findById(bookingId))
        .thenReturn(Mono.just(booking));

    var event = EventDto.builder()
        .id(eventId)
        .title("title")
        .description("description")
        .location("location")
        .cost(BigDecimal.TEN)
        .facilitator("facilitator")
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusHours(10))  // AWAITING
        .durationInMinutes(80)
        .build();
    when(eventRequestService.requestEvent(eventId, token))
        .thenReturn(Mono.just(event));

    when(eventDtoToStatusMapper.apply(event)).thenReturn(EventStatus.AWAITING);

    when(bookingRepository.deleteById(bookingId))
        .thenReturn(Mono.empty()); // can't mimic Mono<Void>, so just returning empty

    StepVerifier.create(webService.deleteBooking(bookingId, token))
        .verifyComplete();
  }

  //endregion Delete Method Scenarios

  //region Helper Methods

  private EventDto buildEventDto(LocalDateTime eventDateTime, Integer duration) {
    return EventDto.builder()
        .id(eventId)
        .title("title")
        .description("description")
        .location("location")
        .cost(BigDecimal.TEN)
        .facilitator("facilitator")
        .availableBookings(10)
        .eventDateTime(eventDateTime)
        .durationInMinutes(duration)
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
    return IntStream.range(1, allBookingsCount + 1)
        .mapToObj(id -> {
          if (id % 2 == 0){
            return new UserIdPair(id, testUser);
          }
          return new UserIdPair(id, null);})
        .map(this::createBooking);
  }

  private record UserIdPair(int id, @Nullable String user) {}

  private Booking createBooking(UserIdPair userIdPair) {
    var user = userIdPair.user() == null ? "User-" + userIdPair.id() : userIdPair.user();
    return Booking.builder()
        .id(userIdPair.id())
        .eventId(userIdPair.id() + 27)
        .username(user)
        .eventDateTime(LocalDateTime.now().plusDays(userIdPair.id()))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();
  }


  //endregion Helper Methods



}
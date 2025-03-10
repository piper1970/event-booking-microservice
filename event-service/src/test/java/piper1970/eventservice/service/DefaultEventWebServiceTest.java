package piper1970.eventservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.Nullable;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.exceptions.EventCancellationException;
import piper1970.eventservice.exceptions.EventTimeoutException;
import piper1970.eventservice.exceptions.EventUpdateException;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("Event Web Service")
class DefaultEventWebServiceTest {

  private DefaultEventWebService webService;

  @Mock
  private EventRepository eventRepository;

  @Mock
  private EventMapper eventMapper;

  @Mock
  private EventDtoToStatusMapper eventDtoToStatusMapper;

  @Mock
  private Clock clock;

  private static final Integer updateCutoffMinutes = 2;
  private static final Integer eventRepositoryTimeoutInMilliseconds = 1000;
  private static final int allEventsCount = 20;
  private static final String facilitator = "facilitator";
  private static final Integer eventId = 1;
  private static final Duration eventDuration = Duration.ofMinutes(
      eventRepositoryTimeoutInMilliseconds);

  @BeforeEach
  void setUp() {

    webService = new DefaultEventWebService(eventRepository,
        eventMapper, eventDtoToStatusMapper, clock, updateCutoffMinutes,
        eventRepositoryTimeoutInMilliseconds);
  }

  //region Get Events Scenarios

  /// ## GET EVENTS SCENARIOS
  /// - repository returns non-empty list -> returns list of events
  /// - repository times out -> returns EventTimeoutException
  /// - repository returns empty list -> returns empty list

  @Test
  @DisplayName("getEvents should throw EventTimeoutException if repo fetch takes too long")
  void getEvents_booking_repo_timeout() {

    setupMockClocks();

    when(eventRepository.findAll()).thenReturn(createEventFlux()
        .delaySequence(eventDuration)
    );

    StepVerifier.withVirtualTime(() -> webService.getEvents())
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  @Test
  @DisplayName("getEvents should be able to retrieve all events")
  void getEvents_events_returned() {

    setupMockClocks();

    when(eventRepository.findAll()).thenReturn(createEventFlux());

    StepVerifier.create(webService.getEvents())
        .expectNextCount(allEventsCount)
        .verifyComplete();
  }

  @Test
  @DisplayName("getEvents should return successfully even if not events are present")
  void getEvents_no_events_returned() {
    when(eventRepository.findAll()).thenReturn(Flux.empty());

    StepVerifier.create(webService.getEvents())
        .verifyComplete();
  }

  //endregion Get Events Scenarios

  //region Get Event Scenarios

  /// ## GET EVENT SCENARIOS
  /// - repo does not find event with given id -> returns Mono.empty()
  /// - repo takes too long -> throws EventTimeoutException Mono
  /// - repo finds event with given id -> returns event

  @Test
  @DisplayName("getEvent should return empty Mono when event with given id not in repo")
  void getEvent_nothing_found() {
    when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

    StepVerifier.create(webService.getEvent(eventId))
        .verifyComplete();
  }

  @Test
  @DisplayName("getEvent should throw EventTimeoutException Mono when repo times out")
  void getEvent_timeout() {

    setupMockClocks();

    var event = this.createEvent(new EventParams(eventId, facilitator));
    when(eventRepository.findById(eventId)).thenReturn(Mono.just(event)
        .delayElement(eventDuration)
    );

    StepVerifier.withVirtualTime(() -> webService.getEvent(eventId))
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  @Test
  @DisplayName("getEvent should return event when event is found in repo via id param")
  void getEvent_returns_event() {

    setupMockClocks();

    var event = this.createEvent(new EventParams(eventId, facilitator));
    when(eventRepository.findById(eventId)).thenReturn(Mono.just(event));

    StepVerifier.create(webService.getEvent(eventId))
        .expectNext(event)
        .verifyComplete();
  }

  //endregion Get Event Scenarios

  //region Create Event Scenarios

  /// ## CREATE EVENT SCENARIOS
  /// - repo saves new event -> new event with updated id is returned
  /// - repo times out -> EventTimeoutException is thrown

  @Test
  @DisplayName("createEvent should return saved event with generated id when repo saves the event properly")
  void createEvent_saved() {

    setupMockClocks();

    var eventCreateRequest = this.createEventRequest(
        new CreateEventRequestParam(eventId, LocalDateTime.now(clock).plusHours(2), 60));
    var event = this.createEvent(new EventParams(eventId, null));

    when(eventMapper.toEntity(eventCreateRequest)).thenReturn(event.withId(null));
    when(eventRepository.save(event)).thenReturn(Mono.just(event));

    StepVerifier.create(webService.createEvent(eventCreateRequest))
        .expectNext(event)
        .verifyComplete();
  }

  @Test
  @DisplayName("createEvent should throw EventTimeoutException when repo call takes too long")
  void createEvent_timeout() {

    setupMockClocks();

    var cre = this.createEventRequest(
        new CreateEventRequestParam(eventId, LocalDateTime.now(clock).plusHours(2), 60));
    var event = this.createEvent(new EventParams(eventId, null));

    when(eventMapper.toEntity(cre)).thenReturn(event.withId(null));
    when(eventRepository.save(event)).thenReturn(Mono.just(event)
        .delayElement(eventDuration));

    StepVerifier.withVirtualTime(() -> webService.createEvent(cre))
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  //endregion Create Event Scenarios

  //region Update Event Scenarios

  /// ## UPDATE EVENT SCENARIOS
  /// - repo times out trying to find event with given id -> EventTimeoutException is thrown
  /// - repo cannot find event with given id -> EventNotFoundException is thrown
  /// - event update merge fails because the update window has passed -> EventUpdateException is
  /// thrown
  /// - repo times out trying to save updated event -> EventTimeoutException is thrown
  /// - repo finds and updated event correctly -> new updated event is returned

  @Test
  @DisplayName("updateEvent should return EventTimeoutException Mono when repo times out trying to find event by id")
  void updateEvent_repository_timeout() {

    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));
    var eventUpdateRequest = createEventUpdateRequest(
        new UpdateEventRequestParam(eventId, LocalDateTime.now(clock).plusHours(2), 90));

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent)
        .delayElement(eventDuration));

    StepVerifier.withVirtualTime(() -> webService.updateEvent(eventId, eventUpdateRequest))
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  @Test
  @DisplayName("updateEvent should return EventNotFoundException Mono when the repo cannot find the event with given id")
  void updateEvent_event_not_found() {

    setupMockClocks();

    var eventUpdateRequest = createEventUpdateRequest(
        new UpdateEventRequestParam(eventId, LocalDateTime.now(clock).plusHours(2), 90));

    when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

    StepVerifier.create(webService.updateEvent(eventId, eventUpdateRequest))
        .verifyError(EventNotFoundException.class);
  }

  @Test
  @DisplayName("updateEvent should return EventUpdateException when the update happens too late (cut-off window has passed)")
  void updateEvent_update_too_late() {

    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));
    originalEvent.setEventDateTime(LocalDateTime.now(clock).minusMinutes(1));

    var eventUpdateRequest = createEventUpdateRequest(
        new UpdateEventRequestParam(eventId, LocalDateTime.now(clock).plusHours(2), 90));

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.IN_PROGRESS);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    StepVerifier.create(webService.updateEvent(eventId, eventUpdateRequest))
        .verifyError(EventUpdateException.class);
  }

  @Test
  @DisplayName("updateEvent should return EventTimeoutException Mono when repo times out trying to save updated event")
  void updateEvent_timeout_on_save() {

    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));

    var updateDurationInMinutes = originalEvent.getDurationInMinutes() + 30;
    var eventUpdateRequest = createEventUpdateRequest(
        new UpdateEventRequestParam(eventId, originalEvent.getEventDateTime(),
            updateDurationInMinutes));

    var updatedEvent = originalEvent.toBuilder()
        .durationInMinutes(updateDurationInMinutes)
        .build();

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.AWAITING);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    when(eventRepository.save(any(Event.class))).thenReturn(Mono.just(updatedEvent)
        .delayElement(eventDuration)
    );

    StepVerifier.withVirtualTime(() -> webService.updateEvent(eventId, eventUpdateRequest))
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  @Test
  @DisplayName("updateEvent should return updated event when the event is found and updated correctly in the repo")
  void updateEvent_update_success() {

    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));

    var updateDurationInMinutes = originalEvent.getDurationInMinutes() + 30;
    var eventUpdateRequest = createEventUpdateRequest(
        new UpdateEventRequestParam(eventId, originalEvent.getEventDateTime(),
            updateDurationInMinutes));

    var updatedEvent = originalEvent.toBuilder()
        .durationInMinutes(updateDurationInMinutes)
        .build();

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.AWAITING);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    when(eventRepository.save(any(Event.class))).thenReturn(Mono.just(updatedEvent)
    );

    StepVerifier.create(webService.updateEvent(eventId, eventUpdateRequest))
        .expectNext(updatedEvent)
        .verifyComplete();
  }

  //endregion Update Event Scenarios

  //region Delete Event Scenarios

  /// ## DELETE EVENT SCENARIOS
  /// - repo times out trying to find event with given id -> EventTimeoutException is thrown
  /// - repo cannot find event with given id -> EventNotFoundException is thrown
  /// - event is in progress -> EventCancellationException is thrown
  /// - repo times out trying to delete event with given id -> EventTimeoutException is thrown
  /// - repo successfully deletes event that hasn't started -> Mono[Void] returned
  /// - repo successfully deletes event that has completed -> Mono[Void] returned

  @Test
  @DisplayName("deleteEvent should return EventTimeoutException Mono when repo call to findById times out")
  void deleteEvent_repo_timeout_finding_event() {

    setupMockClocks();

    var event = createEvent(new EventParams(eventId, facilitator));

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(event)
        .delayElement(eventDuration));

    StepVerifier.withVirtualTime(() -> webService.deleteEvent(eventId))
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  @Test
  @DisplayName("deleteEvent should return EventNotFoundException Mono when repo cannot find event with given id")
  void deleteEvent_cannot_find_event() {

    when(eventRepository.findById(eventId)).thenReturn(Mono.empty());

    StepVerifier.create(webService.deleteEvent(eventId))
        .verifyError(EventNotFoundException.class);
  }

  @Test
  @DisplayName("deleteEvent should return EventCancellationException Mono when event is currently in progress")
  void deleteEvent_event_in_progress() {
    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));
    originalEvent.setEventDateTime(LocalDateTime.now(clock).minusMinutes(1));

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.IN_PROGRESS);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    StepVerifier.create(webService.deleteEvent(eventId))
        .verifyError(EventCancellationException.class);
  }

  @Test
  @DisplayName("deleteEvent should return EventTimeoutException Mono when repo call delete the event times out")
  void deleteEvent_delete_times_out() {
    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));
    originalEvent.setEventDateTime(LocalDateTime.now(clock).minusDays(1));

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(any(EventDto.class))).thenReturn(EventStatus.COMPLETED);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    when(eventRepository.delete(any(Event.class))).thenReturn(Mono.just(mock(Void.class))
        .delayElement(eventDuration));

    StepVerifier.withVirtualTime(() -> webService.deleteEvent(eventId))
        .expectSubscription()
        .thenAwait(eventDuration)
        .verifyError(EventTimeoutException.class);
  }

  @Test
  @DisplayName("deleteEvent should return Void mono when event is successfully deleted from the repo and event completed")
  void deleteEvent_success_event_completed() {

    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));
    originalEvent.setEventDateTime(LocalDateTime.now(clock).minusDays(1));

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(any(EventDto.class))).thenReturn(EventStatus.COMPLETED);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    when(eventRepository.delete(any(Event.class))).thenReturn(Mono.empty());

    StepVerifier.create(webService.deleteEvent(eventId))
        .verifyComplete();
  }

  @Test
  @DisplayName("deleteEvent should return Void mono when event is successfully deleted from the repo and event not started")
  void deleteEvent_success_event_not_started() {
    setupMockClocks();

    var originalEvent = createEvent(new EventParams(eventId, facilitator));
    originalEvent.setEventDateTime(LocalDateTime.now(clock).minusMinutes(1));

    var eventDto = EventDto.builder()
        .id(originalEvent.getId())
        .facilitator(originalEvent.getFacilitator())
        .title(originalEvent.getTitle())
        .description(originalEvent.getDescription())
        .location(originalEvent.getLocation())
        .cost(originalEvent.getCost())
        .availableBookings(originalEvent.getAvailableBookings())
        .eventDateTime(originalEvent.getEventDateTime())
        .durationInMinutes(originalEvent.getDurationInMinutes())
        .build();

    when(eventMapper.toDto(any(Event.class))).thenReturn(eventDto);

    when(eventDtoToStatusMapper.apply(eventDto)).thenReturn(EventStatus.AWAITING);

    when(eventRepository.findById(eventId)).thenReturn(Mono.just(originalEvent));

    when(eventRepository.delete(any(Event.class))).thenReturn(Mono.empty());

    StepVerifier.create(webService.deleteEvent(eventId))
        .verifyComplete();
  }

  //endregion Delete Event Scenarios

  //region Helper Methods

  private void setupMockClocks() {
    Instant clockInstant = Instant.parse("2025-03-05T14:35:00Z");
    ZoneId zoneId = ZoneId.systemDefault();

    given(clock.instant()).willReturn(clockInstant);
    given(clock.getZone()).willReturn(zoneId);
  }

  record CreateEventRequestParam(Integer id, LocalDateTime dateTime,
                                 Integer durationInMinutes) {

  }

  private EventCreateRequest createEventRequest(CreateEventRequestParam param) {
    return EventCreateRequest.builder()
        .facilitator("facilitator-" + param.id())
        .title("title-" + param.id())
        .description("description-" + param.id())
        .location("location-" + param.id())
        .eventDateTime(param.dateTime())
        .durationInMinutes(param.durationInMinutes())
        .cost(BigDecimal.TEN)
        .availableBookings(50)
        .build();
  }

  record UpdateEventRequestParam(Integer id, LocalDateTime dateTime,
                                 Integer durationInMinutes) {

  }

  private EventUpdateRequest createEventUpdateRequest(UpdateEventRequestParam param) {
    return EventUpdateRequest.builder()
        .title("title-" + param.id())
        .description("description-" + param.id())
        .location("location-" + param.id())
        .eventDateTime(param.dateTime())
        .durationInMinutes(param.durationInMinutes())
        .cost(BigDecimal.TEN)
        .availableBookings(25)
        .build();
  }

  private record EventParams(int id, @Nullable String facilitator) {

  }

  private Event createEvent(EventParams param) {
    var user = param.facilitator() == null ? "facilitator-" + param.id() : param.facilitator();
    return Event.builder()
        .id(param.id)
        .facilitator(user)
        .title("title-" + param.id())
        .description("description-" + param.id())
        .location("location-" + param.id())
        .eventDateTime(LocalDateTime.now(clock).plusDays(1))
        .durationInMinutes(60)
        .cost(BigDecimal.TEN)
        .availableBookings(50)
        .build();
  }

  private Flux<Event> createEventFlux() {
    return Flux.fromStream(createEventStream());
  }

  private Stream<Event> createEventStream() {
    return IntStream.range(0, allEventsCount)
        .mapToObj(id -> {
          var params = new EventParams(id + 1, facilitator);
          return createEvent(params);
        });
  }

  //endregion Helper Methods
}
package piper1970.eventservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.EventCreateRequest;
import piper1970.eventservice.dto.EventUpdateRequest;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.exceptions.EventCancellationException;
import piper1970.eventservice.exceptions.EventTimeoutException;
import piper1970.eventservice.exceptions.EventUpdateException;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DefaultEventWebService implements EventWebService {

  private final EventRepository eventRepository;
  private final EventMapper eventMapper;
  private final EventDtoToStatusMapper eventDtoToStatusMapper;
  private final Clock clock;
  private final Integer updateCutoffMinutes;
  private final Integer eventRepositoryTimeoutInMilliseconds;
  private final Duration eventsTimeoutDuration;

  public DefaultEventWebService(
      @NonNull EventRepository eventRepository,
      @NonNull EventMapper eventMapper,
      @NonNull EventDtoToStatusMapper eventDtoToStatusMapper, Clock clock,
      @NonNull @Value("${event.change.cutoff.minutes:30}") Integer updateCutoffMinutes,
      @NonNull @Value("${event-repository.timout.milliseconds}")Integer eventRepositoryTimeoutInMilliseconds) {

    this.eventRepository = eventRepository;
    this.eventMapper = eventMapper;
    this.eventDtoToStatusMapper = eventDtoToStatusMapper;
    this.clock = clock;
    this.updateCutoffMinutes = updateCutoffMinutes;
    this.eventRepositoryTimeoutInMilliseconds = eventRepositoryTimeoutInMilliseconds;
    this.eventsTimeoutDuration = Duration.ofMinutes(eventRepositoryTimeoutInMilliseconds);
  }

  // TODO: needs to talk to bookingService, via kafka

  @Override
  public Flux<Event> getEvents() {
    return eventRepository.findAll()
        // handle timeout of fetch-all by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to get all events"), ex)))
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<Event> getEvent(@NonNull Integer id) {
    return eventRepository.findById(id)
        // handle timeout of fetch by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to get event [%d]".formatted(id)), ex)))
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<Event> createEvent(@NonNull EventCreateRequest createRequest) {
    var event = eventMapper.toEntity(createRequest);

    return eventRepository.save(event)
        // handle timeout of save by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to create event"), ex)))
        // send new-event msg to queue
        .doOnNext(newEvent -> {
          log.debug("Event [{}] has been created", newEvent.getId());
          // TODO: send NewEvent event to kafka (performer)
        });
  }

  @Transactional
  @Override
  public Mono<Event> updateEvent(@NonNull Integer id, @NonNull EventUpdateRequest updateRequest) {
    return eventRepository.findById(id)
        // handle timeouts of initial fetch by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to find event %d for updating".formatted(id)), ex)))
        // if not found, throw EventNotFoundException
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event [%d] not found".formatted(id))))
        // attempt to merge... throws EventUpdateException if merge fails due to timing issues
        .flatMap(event -> mergeWithUpdateRequest(event, updateRequest))
        .flatMap(eventRepository::save)
        // handle timeout of save by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to update event [%d]".formatted(id)), ex)))
        // send update-event msg to queue
        .doOnNext(updatedEvent -> {
          log.debug("Event [{}] has been updated", updatedEvent.getId());
          // TODO: send UpdatedEvent event to kafka (bookings and performer)
        });
  }

  @Transactional
  @Override
  public Mono<Void> deleteEvent(@NonNull Integer id) {
    return eventRepository.findById(id)
        // handle timeout of initial fetch by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to find event [%d] for deletion".formatted(id)), ex)))
        // convert 404 to error
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event [%d} not found".formatted(id))))
        // assure event not in progress. throw error if in progress
        .filter(this::eventNotInProgress)
        .switchIfEmpty(Mono.defer(() -> Mono.error(new EventCancellationException(
            "Cannot cancel event [%s] while it is in progress".formatted(id)))))
        .flatMap(eventRepository::delete)
        // handle timeout of delete by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to delete event [%d}".formatted(id)), ex)))
        // send event-cancellation msg to queue
        .doOnSuccess(_void -> {
          log.debug("Event [{}] has been deleted", id);
          // TODO: send CancelledEvent event to kafka (performer, booking members)
        });
  }

  private void logEventRetrieval(Event event) {
    log.debug("Event {} has been retrieved", event.getId());
  }

  /// Handles logic for merging update with current request
  /// Updates can only happen if the event has not started, and
  /// the updateCutoffMinutes window has already been passed
  private Mono<Event> mergeWithUpdateRequest(Event event, EventUpdateRequest updateRequest) {
    EventStatus adjustedEventStatus = Optional.ofNullable(
            getEventStatus(event.withEventDateTime(updateRequest.getEventDateTime())))
        .orElseGet(() -> getEventStatus(event));
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    LocalDateTime cutoffTime = event.getEventDateTime().minusMinutes(updateCutoffMinutes)
        .truncatedTo(ChronoUnit.MINUTES);

    var safeToChange = now.isBefore(cutoffTime) && adjustedEventStatus == EventStatus.AWAITING;

    if (safeToChange) {
      if (updateRequest.getEventDateTime() != null) {
        event.setEventDateTime(updateRequest.getEventDateTime());
      }
      if (updateRequest.getTitle() != null) {
        event.setTitle(updateRequest.getTitle());
      }
      if (updateRequest.getDescription() != null) {
        event.setDescription(updateRequest.getDescription());
      }
      if (updateRequest.getLocation() != null) {
        event.setLocation(updateRequest.getLocation());
      }
      if (updateRequest.getCost() != null) {
        event.setCost(updateRequest.getCost());
      }
      if (updateRequest.getAvailableBookings() != null) {
        event.setAvailableBookings(updateRequest.getAvailableBookings());
      }
      if (updateRequest.getDurationInMinutes() != null) {
        event.setDurationInMinutes(updateRequest.getDurationInMinutes());
      }
    } else {
      var exception = new EventUpdateException(
          String.format("Cannot update event [%d] after cutoff window [%s]", event.getId(),
              cutoffTime));
      return Mono.error(exception);
    }
    return Mono.just(event);
  }

  private boolean eventNotInProgress(Event event) {
    return getEventStatus(event) != EventStatus.IN_PROGRESS;
  }

  @Nullable
  private EventStatus getEventStatus(Event event) {
    return eventDtoToStatusMapper.apply(eventMapper.toDto(event));
  }

  private String provideTimeoutErrorMessage(String subMessage) {
    return String.format("Event repository timed out [over %d milliseconds] %s",
        eventRepositoryTimeoutInMilliseconds, subMessage);
  }
}

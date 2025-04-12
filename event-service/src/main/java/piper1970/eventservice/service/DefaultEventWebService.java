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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
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
  private final MessagePostingService messagePostingService;
  private final EventMapper eventMapper;
  private final Clock clock;
  private final Integer eventRepositoryTimeoutInMilliseconds;
  private final Duration eventsTimeoutDuration;

  public DefaultEventWebService(
      @NonNull EventRepository eventRepository,
      @NonNull MessagePostingService messagePostingService,
      @NonNull EventMapper eventMapper,
      Clock clock,
      @NonNull @Value("${event-repository.timout.milliseconds}") Integer eventRepositoryTimeoutInMilliseconds) {

    this.eventRepository = eventRepository;
    this.messagePostingService = messagePostingService;
    this.eventMapper = eventMapper;
    this.clock = clock;
    this.eventRepositoryTimeoutInMilliseconds = eventRepositoryTimeoutInMilliseconds;
    this.eventsTimeoutDuration = Duration.ofMinutes(eventRepositoryTimeoutInMilliseconds);
  }

  @Override
  public Flux<EventDto> getEvents() {
    return eventRepository.findAll()
        .map(eventMapper::toDto)
        // handle timeout of fetch-all by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to get all events"), ex)))
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<EventDto> getEvent(@NonNull Integer id) {
    return eventRepository.findById(id)
        .map(eventMapper::toDto)
        // handle timeout of fetch by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to get event [%d]".formatted(id)), ex)))
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<EventDto> createEvent(@NonNull EventCreateRequest createRequest) {
    var event = eventMapper.toEntity(createRequest);

    return eventRepository.save(event)
        .map(eventMapper::toDto)
        // handle timeout of save by throwing EventTimeoutException
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to create event"), ex)))
        .doOnNext(dto -> log.debug("Event {} has been created", dto.getId()));
  }

  @Transactional
  @Override
  public Mono<EventDto> updateEvent(@NonNull Integer id,
      @NonNull EventUpdateRequest updateRequest) {
    return eventRepository.findById(id)
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage(
                    "attempting to find event %d for updating".formatted(id)), ex)))
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event [%d] not found".formatted(id))))
        .flatMap(event -> mergeWithUpdateRequest(event, updateRequest))
        .map(eventMapper::toDto)
        .doOnNext(updatedEvent -> {
          log.debug("Event [{}] has been updated", updatedEvent.getId());
          var message = createEventChangedMessage(updatedEvent);
          messagePostingService.postEventChangedMessage(message);
        });
  }

  @Transactional
  @Override
  public Mono<EventDto> cancelEvent(Integer id, String facilitator) {

    return eventRepository.findByIdAndFacilitator(id, facilitator)
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage(
                    "attempting to find event [%d] for deletion".formatted(id)), ex)))
        .switchIfEmpty(Mono.error(new EventNotFoundException(
            "Event [%d} run by [%s]not found".formatted(id, facilitator))))
        .filter(this::safeToCancel)
        .switchIfEmpty(Mono.error(new EventCancellationException(
            "Cannot cancel event [%d] if the event already in progress or completed"
                .formatted(id))))
        .flatMap(this::cancelAndSave)
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to save cancelled event [%d]".formatted(id)),
                ex)))
        .map(eventMapper::toDto)
        .doOnNext(dto -> {
          log.debug("Event [{}] has been cancelled", dto.getId());
          var message = createEventCancelledMessage(dto);
          messagePostingService.postEventCancelledMessage(message);
        });
  }

  private EventChanged createEventChangedMessage(EventDto updatedEvent) {
    var message = new EventChanged();
    message.setEventId(updatedEvent.getId());
    message.setMessage(updatedEvent.toString());
    return message;
  }

  private EventCancelled createEventCancelledMessage(EventDto dto) {
    var message = new EventCancelled();
    message.setEventId(dto.getId());
    return message;
  }

  private void logEventRetrieval(EventDto event) {
    log.debug("Event {} has been retrieved", event.getId());
  }

  private Mono<Event> cancelAndSave(Event event) {
    if (event.isCancelled()) {
      return Mono.just(event);
    }
    var cancelledEvent = event.toBuilder().cancelled(true).build();
    return eventRepository.save(cancelledEvent);
  }

  /// Handles logic for merging update with current request Updates can only happen if the event has
  /// not started, and the updateCutoffMinutes window has already been passed
  private Mono<Event> mergeWithUpdateRequest(Event event, EventUpdateRequest updateRequest) {

    var isSafeToChange = safeToChange(event, updateRequest.getEventDateTime());

    if (isSafeToChange) {
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
      if (updateRequest.getAvailableBookings() != null) {
        event.setAvailableBookings(updateRequest.getAvailableBookings());
      }
      if (updateRequest.getDurationInMinutes() != null) {
        event.setDurationInMinutes(updateRequest.getDurationInMinutes());
      }
    } else {
      var exception = new EventUpdateException(
          String.format(
              "Cannot update event [%d] once event starts",
              event.getId()));
      return Mono.error(exception);
    }
    return eventRepository.save(event)
        .timeout(eventsTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventTimeoutException(
                provideTimeoutErrorMessage("attempting to update event [%d]".formatted(event.getId())), ex)));

  }

  private boolean safeToCancel(Event event) {
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    LocalDateTime cutoffTime = event.getEventDateTime()
        .truncatedTo(ChronoUnit.MINUTES);
    return now.isBefore(cutoffTime);
  }

  private String provideTimeoutErrorMessage(String subMessage) {
    return String.format("Event repository timed out [over %d milliseconds] %s",
        eventRepositoryTimeoutInMilliseconds, subMessage);
  }

  private boolean safeToChange(Event event, LocalDateTime eventDateTime) {
    if (event.isCancelled()) {
      return false;
    }
    var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    var originalCutoffTime = event.getEventDateTime();

    var adjustedEvent = Optional.ofNullable(eventDateTime)
        .map(event::withEventDateTime)
        .orElse(event);

    var newCutoffTime = adjustedEvent.getEventDateTime()
        .truncatedTo(ChronoUnit.MINUTES);
    return now.isBefore(originalCutoffTime) && now.isBefore(newCutoffTime);
  }

}

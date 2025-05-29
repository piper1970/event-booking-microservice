package piper1970.eventservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.exceptions.KafkaPostingException;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import piper1970.eventservice.exceptions.EventCancellationException;
import piper1970.eventservice.exceptions.EventTimeoutException;
import piper1970.eventservice.exceptions.EventUpdateException;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@Slf4j
public class DefaultEventWebService implements EventWebService {

  private final EventRepository eventRepository;
  private final MessagePostingService messagePostingService;
  private final EventMapper eventMapper;
  private final Clock clock;
  private final Integer eventRepositoryTimeoutInMilliseconds;
  private final Duration eventsTimeoutDuration;
  private final TransactionalOperator transactionalOperator;
  private final Retry defaultRepositoryRetry;
  private final Retry defaultKafkaRetry;

  public DefaultEventWebService(
      @NonNull EventRepository eventRepository,
      @NonNull MessagePostingService messagePostingService,
      @NonNull EventMapper eventMapper,
      TransactionalOperator transactionalOperator,
      Clock clock,
      @NonNull @Value("${event-repository.timout.milliseconds}") Integer eventRepositoryTimeoutInMilliseconds,
      @Qualifier("repository") Retry defaultRepositoryRetry,
      @Qualifier("kafka") Retry defaultKafkaRetry) {

    this.eventRepository = eventRepository;
    this.messagePostingService = messagePostingService;
    this.eventMapper = eventMapper;
    this.transactionalOperator = transactionalOperator;
    this.clock = clock;
    this.eventRepositoryTimeoutInMilliseconds = eventRepositoryTimeoutInMilliseconds;
    this.eventsTimeoutDuration = Duration.ofMinutes(eventRepositoryTimeoutInMilliseconds);
    this.defaultRepositoryRetry = defaultRepositoryRetry;
    this.defaultKafkaRetry = defaultKafkaRetry;
  }

  @Override
  public Flux<EventDto> getEvents() {
    log.debug("Get events called");

    return eventRepository.findAll()
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(eventsTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryFluxTimeout(ex,  "attempting to get all events"))
        .map(eventMapper::toDto)
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<EventDto> getEvent(@NonNull Integer id) {
    log.debug("Get event with id [{}] called", id);

    return eventRepository.findById(id)
        .subscribeOn(Schedulers.boundedElastic())
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event [%d] not found".formatted(id))))
        // ensure status is correct before returning to caller
        .flatMap(this::handlePossibleStatusUpdate)
        .timeout(eventsTimeoutDuration)
        .as(transactionalOperator::transactional)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryException(ex, id, "attempting to get event"))
        .map(eventMapper::toDto)
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<EventDto> createEvent(@NonNull EventCreateRequest createRequest) {
    log.debug("Create event called [{}]", createRequest);

    var event = eventMapper.toEntity(createRequest)
        // EventCreateRequest validation ensures future date
        .withEventStatus(EventStatus.AWAITING);
    return eventRepository.save(event)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(eventsTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryException(ex, 0, "attempting to save event"))
        .map(eventMapper::toDto)
        .doOnNext(dto -> log.debug("Event [{}] has been created", dto));
  }

  @Override
  public Mono<EventDto> updateEvent(@NonNull Integer id,
      @NonNull String facilitator, @NonNull EventUpdateRequest updateRequest) {

    log.debug("Update event with id [{}] called [{}]", id, updateRequest);

    return eventRepository.findByIdAndFacilitator(id, facilitator)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(eventsTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryException(ex, id, "attempting to find event"))
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event [%d] not found for facilitator [%s]".formatted(id, facilitator))))
        .flatMap(event -> mergeWithUpdateRequest(event, updateRequest))
        .flatMap(updatedEvent -> {
          var dto = eventMapper.toDto(updatedEvent);
          var message = createEventChangedMessage(dto);
          return messagePostingService.postEventChangedMessage(message)
              .subscribeOn(Schedulers.boundedElastic())
              .timeout(eventsTimeoutDuration)
              .retryWhen(defaultKafkaRetry)
              .onErrorResume(ex -> handlePostingTimeout(ex, dto.getId(), "EVENT_CHANGED"))
              .then(Mono.just(dto));
        })
        .as(transactionalOperator::transactional);
  }


  @Override
  public Mono<EventDto> cancelEvent(Integer id, String facilitator) {
    log.debug("Cancel event [{}] called by [{}]", id, facilitator);

    return eventRepository.findByIdAndFacilitator(id, facilitator)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(eventsTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryException(ex, id, "attempting to find event"))
        .switchIfEmpty(Mono.error(new EventNotFoundException(
            "Event [%d} run by [%s]not found".formatted(id, facilitator))))
        .filter(this::safeToCancel)
        .switchIfEmpty(Mono.error(new EventCancellationException(
            "Cannot cancel event [%d] if the event already in progress or completed"
                .formatted(id))))
        .flatMap(this::cancelAndSave)
        .flatMap(updatedEvent -> {
          var dto = eventMapper.toDto(updatedEvent);
          var message = createEventCancelledMessage(dto);
          return messagePostingService.postEventCancelledMessage(message)
              .subscribeOn(Schedulers.boundedElastic())
              .timeout(eventsTimeoutDuration)
              .retryWhen(defaultKafkaRetry)
              .onErrorResume(ex -> handlePostingTimeout(ex, dto.getId(), "EVENT_CANCELLED"))
              .then(Mono.just(dto));
        })
        .as(transactionalOperator::transactional);

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
    if (EventStatus.CANCELLED == event.getEventStatus()) {
      return Mono.just(event);
    }
    var cancelledEvent = event.toBuilder().eventStatus(EventStatus.CANCELLED).build();
    return eventRepository.save(cancelledEvent)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(eventsTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .doOnNext(
            savedEvent -> log.debug("Event [{}] has been cancelled in the database", savedEvent.getId()))
        .onErrorResume(ex -> handleRepositoryException(ex, event.getId(), "save cancelled event"));
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
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(eventsTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .doOnNext(dto -> log.debug("Event [{}] has been updated in the database", dto.getId()))
        .onErrorResume(ex -> handleRepositoryException(ex, event.getId(), "update event "));
  }

  private boolean safeToCancel(Event event) {
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    LocalDateTime cutoffTime = event.getEventDateTime()
        .truncatedTo(ChronoUnit.MINUTES);
    return now.isBefore(cutoffTime);
  }

  private Mono<Event> handleRepositoryException(Throwable ex, Integer eventId, String subMessage) {
    if(ex instanceof EventNotFoundException) {
      return Mono.error(ex);
    }
    else if(Exceptions.isRetryExhausted(ex)){
      return Mono.error(new EventTimeoutException(
          provideTimeoutErrorMessage("attempting to %s [%d]. Exhausted all retries".formatted(subMessage, eventId)), ex.getCause()));
    }
    return Mono.error(new EventTimeoutException(
        provideTimeoutErrorMessage("attempting to %s [%d]".formatted(subMessage, eventId)), ex));
  }

  @SuppressWarnings("all")
  private Flux<Event> handleRepositoryFluxTimeout(Throwable ex, String subMessage) {
    if (Exceptions.isRetryExhausted(ex)) {
      return Flux.error(new EventTimeoutException(
          provideTimeoutErrorMessage(
              "attempting to %s . Exhausted all retries".formatted(subMessage)),
          ex.getCause()));
    }
    return Flux.error(new EventTimeoutException(
        provideTimeoutErrorMessage("attempting to %s".formatted(subMessage)), ex));
  }

  private Mono<Void> handlePostingTimeout(Throwable ex, Integer eventId, String subMessage) {
    if(Exceptions.isRetryExhausted(ex)){
      return Mono.error(new KafkaPostingException(
          providePostingTimeoutErrorMessage("attempting to post %s message for event [%d]. Exhausted all retries".formatted(subMessage, eventId)), ex.getCause()));
    }
    return Mono.error(new KafkaPostingException(
        providePostingTimeoutErrorMessage("attempting to post %s message for event [%d]".formatted(subMessage, eventId)), ex));
  }

  private String providePostingTimeoutErrorMessage(String subMessage) {
    return String.format("Message posting for event timed out [over %d milliseconds] %s",
        eventsTimeoutDuration.toMillis(), subMessage);
  }

  private String provideTimeoutErrorMessage(String subMessage) {
    return String.format("Event repository timed out [over %d milliseconds] %s",
        eventRepositoryTimeoutInMilliseconds, subMessage);
  }

  private boolean safeToChange(Event event, LocalDateTime eventDateTime) {
    if (EventStatus.CANCELLED == event.getEventStatus()) {
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

  /**
   * When getting individual event, need to ensure that the status properly updated for downstream
   * processing by calling services
   */
  private Mono<Event> handlePossibleStatusUpdate(Event event){
    // only statuses to update are 'IN_PROGRESS' or 'AWAITING'
    if(EventStatus.CANCELLED == event.getEventStatus() || EventStatus.COMPLETED == event.getEventStatus()){
      return Mono.just(event);
    }
    var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    var startTime = event.getEventDateTime().truncatedTo(ChronoUnit.MINUTES);
    var endTime = event.getEventDateTime().plus(Duration.ofMinutes(event.getDurationInMinutes())).truncatedTo(ChronoUnit.MINUTES);

    EventStatus derivedStatus;
    if(now.isAfter(endTime)){
      derivedStatus = EventStatus.COMPLETED;
    }else if(now.isAfter(startTime)){
      derivedStatus = EventStatus.IN_PROGRESS;
    }else{
      derivedStatus = event.getEventStatus();
    }

    if(derivedStatus == event.getEventStatus()){
      // status is correct, so return as is
      return Mono.just(event);
    }else{
      // status must be updated before returning
      return eventRepository.save(event.withEventStatus(derivedStatus));
    }
  }
}

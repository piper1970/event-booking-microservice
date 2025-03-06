package piper1970.eventservice.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
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
import piper1970.eventservice.exceptions.EventUpdateException;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEventWebService implements EventWebService {

  private final EventRepository eventRepository;

  private final EventMapper eventMapper;

  private final EventDtoToStatusMapper eventDtoToStatusMapper;

  private final Clock clock;

  @Value("${event.change.cutoff.minutes:30}")
  private Integer updateCutoffMinutes;

  // TODO: needs to talk to bookingService, via kafka

  @Override
  public Flux<Event> getEvents() {
    return eventRepository.findAll()
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<Event> getEvent(Integer id) {
    return eventRepository.findById(id)
        .doOnNext(this::logEventRetrieval);
  }

  @Override
  public Mono<Event> createEvent(EventCreateRequest createRequest) {

    var event = eventMapper.toEntity(createRequest);

    return eventRepository.save(event)
        .doOnNext(newEvent -> {
          log.debug("Event [{}] has been created", newEvent.getId());
          // TODO: send NewEvent event to kafka (performer)
        });
  }

  @Transactional
  @Override
  public Mono<Event> updateEvent(Integer id, EventUpdateRequest updateRequest) {
    return eventRepository.findById(id)
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id: " + id)))
        .map(event -> mergeWithUpdateRequest(event, updateRequest))
        .flatMap(eventRepository::save)
        .doOnNext(updatedEvent -> {
          log.debug("Event [{}] has been updated", updatedEvent.getId());
          // TODO: send UpdatedEvent event to kafka (bookings and performer)
        });

  }

  @Transactional
  @Override
  public Mono<Void> deleteEvent(Integer id) {
    return eventRepository.findById(id)
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id: " + id)))
        .filter(this::eventNotInProgress)
        .switchIfEmpty(Mono.defer(() -> Mono.error(new EventCancellationException(
            "Cannot cancel event [%s] while it is in progress".formatted(id)))))
        .flatMap(eventRepository::delete)
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
  private Event mergeWithUpdateRequest(Event event, @NonNull EventUpdateRequest updateRequest) {

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
      throw new EventUpdateException(
          String.format("Cannot update event [%d] after cutoff window [%s]", event.getId(),
              cutoffTime));
    }

    return event;
  }

  private boolean eventNotInProgress(Event event) {
    return getEventStatus(event) != EventStatus.IN_PROGRESS;
  }

  private EventStatus getEventStatus(Event event) {
    return eventDtoToStatusMapper.apply(eventMapper.toDto(event));
  }

}

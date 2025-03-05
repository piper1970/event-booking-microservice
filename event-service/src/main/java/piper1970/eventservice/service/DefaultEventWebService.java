package piper1970.eventservice.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.EventCreateRequest;
import piper1970.eventservice.dto.EventUpdateRequest;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.exceptions.EventCancellationException;
import piper1970.eventservice.exceptions.EventUpdateException;
import piper1970.eventservice.helpers.EventStatusPair;
import static piper1970.eventservice.helpers.EventStatusTransitionValidators.VALIDATION_MAP;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEventWebService implements EventWebService {

  private final EventRepository eventRepository;

  private final EventMapper eventMapper;

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

    event.setEventStatus(EventStatus.AWAITING);

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
        .map(event -> mergeWithUpdateRequest(event, updateRequest))
        .flatMap(eventRepository::save)
        .doOnNext(updatedEvent -> {
          log.debug("Event [{}] has been updated", updatedEvent.getId());
          // TODO: send UpdatedEvent event to kafka (bookings and performer)
        })
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id: " + id)));
  }

  @Transactional
  @Override
  public Mono<Void> deleteEvent(Integer id) {
    return eventRepository.findById(id)
        .filter(event ->  event.getEventStatus() != EventStatus.IN_PROGRESS)
        .switchIfEmpty(Mono.defer(() -> Mono.error(new EventCancellationException("Cannot cancel event [%s] while it is in progress".formatted(id)))))
        .flatMap(eventRepository::delete)
        .doOnSuccess(deletedEvent -> {
          log.debug("Event [{}] has been deleted", id);
          // TODO: send CancelledEvent event to kafka (performer, booking members)
        });
  }

  private void logEventRetrieval(Event event) {
    log.debug("Event {} has been retrieved", event.getId());
  }

  private Event mergeWithUpdateRequest(Event event, @NonNull EventUpdateRequest updateRequest) {

    // handle status updates first
    EventStatus updateStatus = null;

    if(updateRequest.getEventStatus() != null) {

      final String updateStatusMessage = updateRequest.getEventStatus();

      updateStatus = EventStatus.valueOf(updateStatusMessage);

      // more complex logic needed for transition states.
      // handled in a separate validator class

      var eventStatusTransitionValidator = Optional.ofNullable(VALIDATION_MAP.get(
              EventStatusPair.of(event.getEventStatus(),updateStatus)))
          .orElseThrow(() -> new RuntimeException("Unrecognized event status transition: %s to %s".formatted(event.getEventStatus().name(), updateStatusMessage)));

      // throws EventUpdateException if the given transition is not logical.
      eventStatusTransitionValidator.validate(event.getEventStatus(), updateStatus);

      event.setEventStatus(EventStatus.valueOf(updateRequest.getEventStatus()));
    }


    var notSafeToChange = event.getEventStatus() != EventStatus.AWAITING ||
        updateStatus == EventStatus.IN_PROGRESS ||
        updateStatus == EventStatus.COMPLETED;

    if(updateRequest.getTitle() != null) {
      // no foul if title is changed throughout the event lifecycle
      event.setTitle(updateRequest.getTitle());
    }
    if(updateRequest.getDescription() != null) {
      // no foul if description is changed throughout the event lifecycle
      event.setDescription(updateRequest.getDescription());
    }
    if(updateRequest.getLocation() != null) {
      if(notSafeToChange) {
        handleUpdateErrors(event.getId(), "location");
      }
      event.setLocation(updateRequest.getLocation());
    }
    if(updateRequest.getEventDateTime() != null) {
      if(notSafeToChange) {
        handleUpdateErrors(event.getId(), "eventDateTime");
      }
      event.setEventDateTime(updateRequest.getEventDateTime());
    }
    if(updateRequest.getCost() != null) {
      if(notSafeToChange) {
        handleUpdateErrors(event.getId(), "cost");
      }
      event.setCost(updateRequest.getCost());
    }
    if(updateRequest.getAvailableBookings() != null) {
      if(notSafeToChange) {
        handleUpdateErrors(event.getId(), "availableBookings");
      }
      event.setAvailableBookings(updateRequest.getAvailableBookings());
    }

    return event;
  }

  void handleUpdateErrors(Integer id, String field) throws EventUpdateException {
    throw new EventUpdateException(String.format("Cannot update event [%d] %s once it has started", id, field));
  }
}

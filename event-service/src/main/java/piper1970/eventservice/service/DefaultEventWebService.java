package piper1970.eventservice.service;

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
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEventWebService implements EventWebService {

  private final EventRepository eventRepository;

  private final EventMapper eventMapper;

  // TODO: needs to talk to bookingService, either via kafka

  @Override
  public Flux<Event> getEvents() {
    return eventRepository.findAll();
  }

  @Override
  public Mono<Event> getEvent(Integer id) {
    return eventRepository.findById(id);
  }

  @Override
  public Mono<Event> createEvent(EventCreateRequest createRequest) {
    // database generates id upon insert
    var event = eventMapper.toEntity(createRequest);
    event.setEventStatus(EventStatus.IN_PROGRESS);
    return eventRepository.save(event)
        .doOnNext(newEvent -> {
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
          // TODO: send UpdatedEvent event to kafka (bookings and performer)
        })
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id: " + id)));
  }

  @Override
  public Mono<Void> cancelEvent(Integer id) {
    return eventRepository.deleteById(id)
        .doOnSuccess(deletedEvent -> {
          // TODO: send CancelledEvent event to kafka (performer, booking members)
        });
  }

  private Event mergeWithUpdateRequest(Event event, @NonNull EventUpdateRequest updateRequest) {

    if(updateRequest.getTitle() != null) {
      event.setTitle(updateRequest.getTitle());
    }
    if(updateRequest.getDescription() != null) {
      event.setDescription(updateRequest.getDescription());
    }
    if(updateRequest.getLocation() != null) {
      event.setLocation(updateRequest.getLocation());
    }
    if(updateRequest.getEventDateTime() != null) {
      event.setEventDateTime(updateRequest.getEventDateTime());
    }
    if(updateRequest.getCost() != null) {
      event.setCost(updateRequest.getCost());
    }
    if(updateRequest.getAvailableBookings() != null) {
      event.setAvailableBookings(updateRequest.getAvailableBookings());
    }
    if(updateRequest.getEventStatus() != null) {
      event.setEventStatus(EventStatus.valueOf(updateRequest.getEventStatus()));
    }
    return event;
  }
}

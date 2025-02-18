package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEventWebService implements EventWebService {
  private final EventRepository eventRepository;

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
  public Mono<Event> createEvent(Event event) {
    // database generates id upon insert
    return eventRepository.save(event.withId(null))
        .doOnNext(newEvent -> {
          // TODO: send NewEvent event to kafka (performer)
        });
  }

  @Transactional
  @Override
  public Mono<Event> updateEvent(Event event) {
    return eventRepository.existsById(event.getId())
        .filter(Boolean.TRUE::equals)
        .flatMap(_ignored -> eventRepository.save(event))
        .doOnNext(updatedEvent -> {
          // TODO: send UpdatedEvent event to kafka (bookings and performer)
        })
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id: " + event.getId())));
  }

  @Override
  public Mono<Void> cancelEvent(Integer id) {
    return eventRepository.deleteById(id)
        .doOnSuccess(deletedEvent -> {
          // TODO: send CancelledEvent event to kafka (performer, booking members)
        });
  }
}

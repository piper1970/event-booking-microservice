package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.exceptions.EventNotFoundException;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEventService implements EventService {
  private final EventRepository eventRepository;

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
    return eventRepository.save(event.withId(null));
  }

  @Transactional
  @Override
  public Mono<Event> updateEvent(Event event) {
    // TODO: need to notify bookings/members
    return eventRepository.findById(event.getId())
        .flatMap(_ignored -> eventRepository.save(event))
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id: " + event.getId())));
  }

  @Override
  public Mono<Void> deleteEvent(Integer id) {
    // TODO: need to notify bookings/members
    return eventRepository.deleteById(id);
  }

}

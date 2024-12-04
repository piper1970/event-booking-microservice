package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import piper1970.eventservice.domain.Event;
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

}

package piper1970.eventservice.service;

import piper1970.eventservice.domain.Event;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventService {
  Flux<Event> getEvents();
  Mono<Event> getEvent(Integer id);
}

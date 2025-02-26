package piper1970.eventservice.service;

import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.EventCreateRequest;
import piper1970.eventservice.dto.EventUpdateRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventWebService {
  Flux<Event> getEvents();
  Mono<Event> getEvent(Integer id);
  Mono<Event> createEvent(EventCreateRequest event);
  Mono<Event> updateEvent(Integer id, EventUpdateRequest event);
  Mono<Void> cancelEvent(Integer id);
}

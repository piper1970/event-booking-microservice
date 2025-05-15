package piper1970.eventservice.service;

import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventWebService {
  Flux<EventDto> getEvents();
  Mono<EventDto> getEvent(Integer id);
  Mono<EventDto> createEvent(EventCreateRequest event);
  Mono<EventDto> updateEvent(Integer id, String facilitator, EventUpdateRequest event);
  Mono<EventDto> cancelEvent(Integer id, String facilitator);
}

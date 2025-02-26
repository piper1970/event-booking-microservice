package piper1970.bookingservice.service;

import piper1970.eventservice.common.events.dto.EventDto;
import reactor.core.publisher.Mono;

public interface EventRequestService {
  Mono<EventDto> requestEvent(Integer eventId, String token);
}

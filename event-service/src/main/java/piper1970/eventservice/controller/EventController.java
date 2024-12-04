package piper1970.eventservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.service.EventService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/v1/events")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;

  @GetMapping
  public Flux<Event> getEvents() {
    return eventService.getEvents();
  }

  @GetMapping("{id}")
  public Mono<Event> getEvent(@PathVariable Integer id) {
    return eventService.getEvent(id);
  }

}

package piper1970.eventservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.dto.model.EventDto;
import piper1970.eventservice.service.EventService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/v1/events")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;
  private final EventMapper eventMapper;

  @GetMapping
  public Flux<EventDto> getEvents() {
    return eventService.getEvents()
        .map(eventMapper::toDto);
  }

  @GetMapping("{id}")
  public Mono<EventDto> getEvent(@PathVariable Integer id) {
    return eventService.getEvent(id)
        .map(eventMapper::toDto);
  }

}

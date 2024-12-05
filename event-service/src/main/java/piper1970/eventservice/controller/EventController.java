package piper1970.eventservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.dto.model.EventDto;
import piper1970.eventservice.exceptions.EventNotFoundException;
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
        .map(eventMapper::toDto)
        .switchIfEmpty(Mono.error(new EventNotFoundException("Event not found for id " + id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<EventDto> createEvent(@Valid @RequestBody EventDto eventDto) {
    return eventService.createEvent(eventMapper.toEntity(eventDto))
        .map(eventMapper::toDto);
  }

  @PutMapping("{id}")
  public Mono<EventDto> updateEvent(@PathVariable Integer id, @Valid @RequestBody EventDto eventDto) {
    return eventService.updateEvent(eventMapper.toEntity(eventDto).withId(id))
        .map(eventMapper::toDto);
  }

  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> deleteEvent(@PathVariable Integer id) {
    return eventService.deleteEvent(id);
  }
}

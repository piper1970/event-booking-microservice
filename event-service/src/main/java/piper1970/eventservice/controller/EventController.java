package piper1970.eventservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.service.EventWebService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/events")
@RequiredArgsConstructor
public class EventController {

  private final EventWebService eventWebService;
  private final EventMapper eventMapper;

  @GetMapping
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<EventDto> getEvents() {
    return eventWebService.getEvents()
        .map(eventMapper::toDto);
  }

  @GetMapping("{id}")
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<EventDto> getEvent(@PathVariable Integer id) {
    return eventWebService.getEvent(id)
        .map(eventMapper::toDto);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> createEvent(@Valid @RequestBody EventDto eventDto) {
    return eventWebService.createEvent(eventMapper.toEntity(eventDto))
        .map(eventMapper::toDto);
  }

  @PutMapping("{id}")
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<EventDto> updateEvent(@PathVariable Integer id, @Valid @RequestBody EventDto eventDto) {
    return eventWebService.updateEvent(eventMapper.toEntity(eventDto).withId(id))
        .map(eventMapper::toDto);
  }

  @DeleteMapping("{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('ADMIN')")
  public Mono<Void> cancelEvent(@PathVariable Integer id) {
    return eventWebService.cancelEvent(id);
  }
}

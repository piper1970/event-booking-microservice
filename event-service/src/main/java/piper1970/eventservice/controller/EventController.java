package piper1970.eventservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.tokens.TokenUtilities;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import piper1970.eventservice.service.EventWebService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

  private final EventWebService eventWebService;

  @GetMapping
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<EventDto> getEvents(@AuthenticationPrincipal JwtAuthenticationToken jwtToken) {

    if(log.isDebugEnabled()) {
      var user = TokenUtilities.getUserFromToken(jwtToken);
      log.debug("User [{}] is retrieving all events", user);
    }

    return eventWebService.getEvents();
  }

  @GetMapping("{id}")
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<EventDto> getEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id) {

    if(log.isDebugEnabled()) {
      var user = TokenUtilities.getUserFromToken(jwtToken);
      log.debug("User [{}] is retrieving event [{}]", user, id);
    }

    return eventWebService.getEvent(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> createEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @Valid @RequestBody EventCreateRequest createRequest) {

    var user = TokenUtilities.getUserFromToken(jwtToken);
    createRequest.setFacilitator(user);
    log.debug("Facilitator [{}] is creating event", user);

    return eventWebService.createEvent(createRequest);
  }

  @PutMapping("{id}")
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> updateEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id,
      @Valid @RequestBody EventUpdateRequest updateRequest) {

    var facilitator = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Facilitator [{}] is updating event [{}]", facilitator, id);

    return eventWebService.updateEvent(id, facilitator, updateRequest);
  }

  @PatchMapping("{id}/cancel")
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> cancelEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @PathVariable Integer id){

    var facilitator = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Cancel event[{}] called by [{}]", id, facilitator);

    return eventWebService.cancelEvent(id, facilitator);
  }
}

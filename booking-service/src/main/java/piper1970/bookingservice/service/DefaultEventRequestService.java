package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import piper1970.eventservice.common.events.dto.EventDto;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEventRequestService implements EventRequestService {

  private final WebClient.Builder webClientBuilder;

  @Override
  public Mono<EventDto> requestEvent(Integer eventId, String token) {

    log.debug("Making request to /api/events/{}", eventId);

    return webClientBuilder
        .build()
        .get()
        .uri("/api/events/{eventId}", eventId)
        .accept(MediaType.APPLICATION_JSON)
        .headers(httpHeaders -> httpHeaders.setBearerAuth(token))
        .retrieve()
        .bodyToMono(EventDto.class)
        .doOnNext(eventDto -> {
          log.debug("Event [{}] has been retrieved", eventId);
        }).doOnError(throwable -> {
          log.error("Event [{}] could not be retrieved", eventId, throwable);
        })
        .onErrorResume(WebClientResponseException.class, ex ->
            ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) ? Mono.empty() : Mono.error(ex));

  }
}

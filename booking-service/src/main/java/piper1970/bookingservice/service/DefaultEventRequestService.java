package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import piper1970.eventservice.common.events.dto.EventDto;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DefaultEventRequestService implements EventRequestService {

  private final WebClient.Builder webClientBuilder;

  @Override
  public Mono<EventDto> requestEvent(Integer eventId, String token) {
    return webClientBuilder
        .build()
        .get()
        .uri("/{eventId}", eventId)
        .accept(MediaType.APPLICATION_JSON)
        .headers(httpHeaders -> httpHeaders.setBearerAuth(token))
        .retrieve()
        .bodyToMono(EventDto.class)
        .onErrorResume(WebClientResponseException.class, ex ->
            ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) ? Mono.empty() : Mono.error(ex));
  }
}

package piper1970.bookingservice.service;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import piper1970.bookingservice.exceptions.EventRequestServiceTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceUnavailableException;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.exceptions.EventForbiddenException;
import piper1970.eventservice.common.exceptions.EventUnauthorizedException;
import piper1970.eventservice.common.exceptions.UnknownCauseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DefaultEventRequestService implements EventRequestService {

  private final WebClient.Builder webClientBuilder;
  private final Long eventTimeoutInMilliseconds;
  private final Duration eventTimeoutDuration;

  public DefaultEventRequestService(WebClient.Builder webClientBuilder,
      @Value("${event-request-service.timeout.milliseconds}") Long eventTimeoutInMilliseconds) {
    this.webClientBuilder = webClientBuilder;
    this.eventTimeoutInMilliseconds = eventTimeoutInMilliseconds;
    this.eventTimeoutDuration = Duration.ofMillis(eventTimeoutInMilliseconds);
  }

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
        .onStatus(HttpStatusCode::is5xxServerError, resp ->
            Mono.error(new EventRequestServiceUnavailableException("Event Request Service Temporarily Unavailable. Please try back later")))
        .onStatus(HttpStatusCode::is4xxClientError, this::handle400Response)
        .bodyToMono(EventDto.class)
        .doOnNext(eventDto -> {
          log.debug("Event [{}] has been retrieved", eventId);
        }).doOnError(throwable -> {
          log.error("Event [{}] could not be retrieved", eventId, throwable);
        })
        .timeout(eventTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new EventRequestServiceTimeoutException(
                "Event Request Service timed out [over %d milliseconds] fetching event".formatted(
                    eventTimeoutInMilliseconds),
                ex)));
  }

  private Mono<? extends Throwable> handle400Response(ClientResponse clientResponse) {
    return switch(clientResponse.statusCode()){
      case HttpStatus.NOT_FOUND -> Mono.empty(); // error handling for this scenario propagates to WebService
      case HttpStatus.UNAUTHORIZED -> Mono.error(new EventUnauthorizedException("User unauthorized to access event-service resource"));
      case HttpStatus.FORBIDDEN -> Mono.error(new EventForbiddenException("User does not have permission to retrieve all events from event-service"));
      default -> Mono.error(new UnknownCauseException("This should not be happening... Unhandled status code: " + clientResponse.statusCode()));
    };
  }
}

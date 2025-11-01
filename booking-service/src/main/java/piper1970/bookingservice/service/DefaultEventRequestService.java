package piper1970.bookingservice.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
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
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.exceptions.EventUnauthorizedException;
import piper1970.eventservice.common.exceptions.UnknownCauseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Default reactive event request service for communicating with event-service microservice to capture
 * current availabilities for requested events.
 * Utilizes circuit-breaker logic with fallback behavior to prevent performance degradation from non-responsive event-service.
 * Maintains time-limits with retry behavior to ensure responsiveness.
 */
@Service
@Slf4j
public class DefaultEventRequestService implements EventRequestService {

  private final WebClient.Builder webClientBuilder;
  private final ReactiveCircuitBreaker circuitBreaker;
  private final Long eventTimeoutInMilliseconds;
  private final Duration eventTimeoutDuration;
  private final Retry defaultEventServiceRetry;

  public DefaultEventRequestService(WebClient.Builder webClientBuilder,
      ReactiveResilience4JCircuitBreakerFactory circuitBreakerFactory,
      @Value("${event-request-service.timeout.milliseconds}") Long eventTimeoutInMilliseconds,
      @Qualifier("event-service") Retry defaultEventServiceRetry) {
    circuitBreaker = circuitBreakerFactory.create("event-request-service");
    this.webClientBuilder = webClientBuilder;
    this.eventTimeoutInMilliseconds = eventTimeoutInMilliseconds;
    this.eventTimeoutDuration = Duration.ofMillis(eventTimeoutInMilliseconds);
    this.defaultEventServiceRetry = defaultEventServiceRetry;
  }

  @Override
  public Mono<EventDto> requestEvent(Integer eventId, String token) {

    log.debug("Making request to /api/events/{}", eventId);

    return webClientBuilder
        .build()
        .get()
        .uri("/api/events/{eventId}", eventId)
        .accept(MediaType.APPLICATION_JSON)
        // authorize with token passed through by callee
        .headers(httpHeaders -> httpHeaders.setBearerAuth(token))
        .retrieve()
        // handle 500 responses
        .onStatus(HttpStatusCode::is5xxServerError, resp ->
            Mono.error(new EventRequestServiceUnavailableException(
                "Event Request Service Temporarily Unavailable. Please try back later")))
        // handle 400 responses
        .onStatus(HttpStatusCode::is4xxClientError, resp -> this.handle400Response(resp, eventId))
        .bodyToMono(EventDto.class)
        // TODO: consider VirtualThreads here
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(eventDto -> log.debug("Event [{}] has been retrieved", eventId))
        .doOnError(throwable -> log.error("Event [{}] could not be retrieved", eventId, throwable))
        .timeout(eventTimeoutDuration)
        .retryWhen(defaultEventServiceRetry) // only retries for timeouts
        .onErrorResume(ex -> {
          if (Exceptions.isRetryExhausted(ex)) {
            // timeout retries are exhausted
            return Mono.error(new EventRequestServiceTimeoutException(
                "Event Request Service timed out [over %d milliseconds] fetching event".formatted(
                    eventTimeoutInMilliseconds),
                ex));
          }
          return Mono.error(ex); // let everything else pass through
        })
        // deal with circuit-breaker logic
        .transform(mono -> circuitBreaker.run(mono, this::fallbackFunction));
  }

  /**
   * Handler function for when circuit-breaker trips
   */
  private Mono<EventDto> fallbackFunction(Throwable throwable) {
    // NOTE from resilience4J:
    // https://github.com/resilience4j/resilience4j/issues/2026#issuecomment-1783781570
    // ignoreExceptions field in the configuration DOES NOT prevent fallback handler from running.
    // it only determines whether CircuitBreaker should consider this a failure or not
    // ALL errors will pass through this function
    return switch (throwable) {
      case EventNotFoundException i -> Mono.error(i);
      case EventUnauthorizedException i -> Mono.error(i);
      case EventForbiddenException i -> Mono.error(i);
      case UnknownCauseException i -> Mono.error(i);
      default -> {
        log.warn("Unexpected exception", throwable);
        yield Mono.error(
            new EventRequestServiceUnavailableException("Unexpected exception", throwable));
      }
    };
  }

  /**
   * Handler function for 400-level responses
   */
  private Mono<? extends Throwable> handle400Response(ClientResponse clientResponse,
      Integer eventId) {

    return switch (clientResponse.statusCode()) {

      case HttpStatus.NOT_FOUND ->
          Mono.error(new EventNotFoundException(createEventNotFountMessage(eventId)));

      case HttpStatus.UNAUTHORIZED -> Mono.error(
          new EventUnauthorizedException("User unauthorized to access event-service resource"));

      case HttpStatus.FORBIDDEN -> Mono.error(new EventForbiddenException(
          "User does not have permission to retrieve all events from event-service"));

      default -> Mono.error(new UnknownCauseException(
          "This should not be happening... Unhandled status code: " + clientResponse.statusCode()));
    };
  }

  /**
   * Create templated event-not-found message with eventId
   */
  private String createEventNotFountMessage(Integer eventId) {
    return String.format("Event [%d] not found", eventId);
  }
}

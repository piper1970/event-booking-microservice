package piper1970.bookingservice.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import piper1970.bookingservice.exceptions.EventRequestServiceTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceUnavailableException;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.exceptions.EventForbiddenException;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import piper1970.eventservice.common.exceptions.EventUnauthorizedException;
import piper1970.eventservice.common.exceptions.UnknownCauseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@DisplayName("EventRequestServiceTest")
@TestClassOrder(OrderAnnotation.class)
@Order(2)
@ExtendWith(MockitoExtension.class)
class DefaultEventRequestServiceTests {

  private DefaultEventRequestService requestService;

  private static final Long timeoutInMillis = 2000L;
  private static final String token = "token";
  private static final String bearerToken = "Bearer " + token;
  private static final int eventId = 1;
  private final Retry retry = Retry.backoff(3, Duration.ofMillis(500L))
      .filter(throwable -> throwable instanceof TimeoutException)
      .jitter(0.7D);
  private static final EventDto testEventResponse;
  private static final String testResponseJson;

  @Mock
  private ReactiveResilience4JCircuitBreakerFactory mockCircuitBreakerFactory;

  @Mock
  private ReactiveCircuitBreaker mockCircuitBreaker;

  static {

    testEventResponse = EventDto.builder()
        .id(eventId)
        .facilitator("facilitator")
        .title("title")
        .description("description")
        .location("location")
        .availableBookings(100)
        .eventDateTime(LocalDateTime.now().plusDays(1))
        .durationInMinutes(90)
        .build();

    var objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build();

    try {
      testResponseJson = objectMapper.writeValueAsString(testEventResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  // Use wiremock mock web server rather than mocking web-client-builder
  @RegisterExtension
  static WireMockExtension mockWebServer = WireMockExtension.newInstance()
      .options(WireMockConfiguration.wireMockConfig()
          .dynamicPort()
          .notifier(new ConsoleNotifier(true))
      ).build();

  @BeforeEach
  void setUp(){

    // setup mock circuit-breaker behavior
    when(mockCircuitBreakerFactory.create(anyString())).thenReturn(mockCircuitBreaker);
    when(mockCircuitBreaker.run(ArgumentMatchers.<Mono<EventDto>>any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String baseUrl = mockWebServer.baseUrl();
    Builder clientBuilder = WebClient.builder()
        .baseUrl(baseUrl);

    requestService = new DefaultEventRequestService(clientBuilder, mockCircuitBreakerFactory, timeoutInMillis, retry);
  }

  /// # TEST SCENARIOS
  /// - webClient returns 500-ish response status -> returns Mono with
  /// EventRequestServiceUnavailableException
  /// - webClient returns Forbidden(403) response status -> returns Mono with ForbiddenException
  /// - webClient returns Unauthorized(401) response status -> returns Mono with
  /// UnauthorizedException
  /// - webClient returns Not_Found(404) -> returns error Mono with EventNotFoundException
  /// - webclient returns other 400-ish response status -> returns Mono with UnknownCauseException
  /// - webClient times out -> returns Mono with EventRequestServiceTimeoutException
  /// - webClient returns no errors -> Mono with Event returned to caller

  @Test
  @DisplayName("requestEvent should return EventRequestServiceUnavailableException if call to webClient returns status code 500 (Internal Server Error)")
  void requestEvent_webClientReturns500(){

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(500))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventRequestServiceUnavailableException.class);
  }

  @Test
  @DisplayName("requestEvent should return ForbiddenException if call to webClient returns status code 403 (Forbidden)")
  void requestEvent_webClientReturns403() {

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(403))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventForbiddenException.class);
  }

  @Test
  @DisplayName("requestEvent should return UnauthorizedException if call to webClient returns status code 401 (Unauthorized)")
  void requestEvent_webClientReturns401() {

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(401))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventUnauthorizedException.class);
  }

  @Test
  @DisplayName("requestEvent should return EventNotFound exception if call to webClient returns status code 404 (Not Found)")
  void requestEvent_webClientReturns404() {

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(404))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventNotFoundException.class);
  }

  @Test
  @DisplayName("requestEvent should return UnknownCauseException if call to webClient status code 400 (Bad Request)")
  void requestEvent_400() {

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(400))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(UnknownCauseException.class);
  }

  @Test
  @DisplayName("requestEvent should return EventRequestServiceTimeoutException if call to webClient times out")
  void requestEvent_times_out() throws JsonProcessingException {

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(testResponseJson)
        ));

    StepVerifier.withVirtualTime(() -> requestService.requestEvent(eventId, token))
        .expectSubscription()
        .thenAwait(Duration.ofMillis(timeoutInMillis * 10))
        .verifyError(EventRequestServiceTimeoutException.class);
  }

  @Test
  @DisplayName("requestEvent should return Event if call to webClient returns status code 200 (Ok) and a response body")
  void requestEvent_success() throws JsonProcessingException {
    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bearerToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(testResponseJson))
        );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .expectNext(testEventResponse)
        .verifyComplete();
  }
}
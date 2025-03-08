package piper1970.bookingservice.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import piper1970.bookingservice.exceptions.EventRequestServiceTimeoutException;
import piper1970.bookingservice.exceptions.EventRequestServiceUnavailableException;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.exceptions.EventForbiddenException;
import piper1970.eventservice.common.exceptions.EventUnauthorizedException;
import piper1970.eventservice.common.exceptions.UnknownCauseException;
import reactor.test.StepVerifier;

@DisplayName("EventRequestServiceTest")
class DefaultEventRequestServiceTest {

  private DefaultEventRequestService requestService;

  private static final Long timeoutInMillis = 2000L;
  private static final String token = "token";
  private static final int eventId = 1;

  private static final ObjectMapper objectMapper;

  static {
    objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build();
  }

  @RegisterExtension
  static WireMockExtension mockWebServer = WireMockExtension.newInstance()
      .options(WireMockConfiguration.wireMockConfig()
          .dynamicPort()
          .notifier(new ConsoleNotifier(true))
      ).build();

  @BeforeEach
  void setUp() {
    String baseUrl = mockWebServer.baseUrl();
    Builder clientBuilder = WebClient.builder()
        .baseUrl(baseUrl);
    requestService = new DefaultEventRequestService(clientBuilder, timeoutInMillis);
  }

  /// ## TEST SCENARIOS
  /// - webClient returns 500-ish response status -> returns Mono with
  /// EventRequestServiceUnavailableException
  /// - webClient returns Forbidden(403) response status -> returns Mono with ForbiddenException
  /// - webClient returns Unauthorized(401) response status -> returns Mono with
  /// UnauthorizedException
  /// - webClient returns Not_Found(404) -> returns empty Mong -> error handling propagates to
  /// caller for this
  /// - webclient returns other 400-ish response status -> returns Mono with UnknownCauseException
  /// - webClient times out -> returns Mono with EventRequestServiceTimeoutException
  /// - webClient returns no errors -> Mono with Event returned to caller

  @Test
  @DisplayName("requestEvent should return EventRequestServiceUnavailableException if call to webClient returns status code 500 (Internal Server Error)")
  void requestEvent_webClientReturns500() throws InterruptedException {
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(500))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventRequestServiceUnavailableException.class);
  }

  @Test
  @DisplayName("requestEvent should return ForbiddenException if call to webClient returns status code 403 (Forbidden)")
  void requestEvent_webClientReturns403() {
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(403))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventForbiddenException.class);
  }

  @Test
  @DisplayName("requestEvent should return UnauthorizedException if call to webClient returns status code 401 (Unauthorized)")
  void requestEvent_webClientReturns401() {
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(401))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(EventUnauthorizedException.class);
  }

  @Test
  @DisplayName("requestEvent should empty Mono if call to webClient returns status code 404 (Not Found)")
  void requestEvent_webClientReturns404() {
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(404))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyComplete();
  }

  @Test
  @DisplayName("requestEvent should return UnknownCauseException if call to webClient status code 400 (Bad Request)")
  void requestEvent_400() {
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(400))
    );

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .verifyError(UnknownCauseException.class);
  }

  @Test
  @DisplayName("requestEvent should return EventRequestServiceTimeoutException if call to webClient times out")
  void requestEvent_times_out() throws JsonProcessingException {
    var response = buildEvent();
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response))
        ));

    StepVerifier.withVirtualTime(() -> requestService.requestEvent(eventId, token))
        .expectSubscription()
        .thenAwait(Duration.ofMillis(timeoutInMillis))
        .verifyError(EventRequestServiceTimeoutException.class);
  }

  @Test
  @DisplayName("requestEvent should return Event if call to webClient returns status code 200 (Ok) and a response body")
  void requestEvent_success() throws JsonProcessingException {
    var response = buildEvent();
    var bToken = "Bearer " + token;

    mockWebServer.stubFor(get("/api/events/" + eventId)
        .withHeader(HttpHeaders.AUTHORIZATION, equalTo(bToken))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response))
        ));

    StepVerifier.create(requestService.requestEvent(eventId, token))
        .expectNext(response)
        .verifyComplete();
  }

  private EventDto buildEvent() {
    return EventDto.builder()
        .id(eventId)
        .facilitator("facilitator")
        .title("title")
        .description("description")
        .location("location")
        .cost(BigDecimal.TEN)
        .availableBookings(100)
        .eventDateTime(LocalDateTime.now().plusDays(1))
        .durationInMinutes(90)
        .build();
  }
}
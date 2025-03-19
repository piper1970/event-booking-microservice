package piper1970.eventservice.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.eventservice.EventServiceTestConfiguration;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@DisplayName("EventController")
@ActiveProfiles("test")
@Import(EventServiceTestConfiguration.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({
    @ConfigureWireMock(
        name = "keystore-service",
        baseUrlProperties = "keystore-service.url")
})
public class EventControllerTests {

  // TODO: fix using spring-kafka-test

  //region Properties Setup

  static RSAKey rsaKey;

  @LocalServerPort
  Integer port;

  @Autowired
  EventMapper eventMapper;

  @MockitoBean
  Clock clock;

  EventDtoToStatusMapper eventDtoToStatusMapper;

  @Autowired
  ObjectMapper objectMapper;

  final Instant clockInstant = Instant.now();
  final ZoneId clockZone = ZoneId.systemDefault();
  final String dbInitializationFailure = "Database failed to initialize for testing";

  @InjectWireMock("keystore-service")
  WireMockServer keycloakServer;
  boolean keycloakServerInitialized;

  @Autowired
  EventRepository eventRepository;

  @Value("${oauth2.realm}")
  String realm;

  @Value("${oauth2.client.id}")
  String oauthClientId;

  WebTestClient webClient;

  //endregion Properties Setup

  @BeforeEach
  void setUp() throws JOSEException, JsonProcessingException {

    given(clock.instant()).willReturn(clockInstant);
    given(clock.getZone()).willReturn(clockZone);

    eventDtoToStatusMapper = new EventDtoToStatusMapper(clock);

    webClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
    eventRepository.deleteAll()
        .then()
        .block();
    setupKeyCloakServer();
  }

  //region GET ALL EVENTS

  @Test
  @DisplayName("authorized members should be able to fetch all events")
  void getAllEvents_Authorized() throws JOSEException {

    var db = initializeDatabase()
        .block();
    var token = getJwtToken("test_member", "MEMBER");
    webClient.get()
        .uri("/api/events")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(EventDto.class)
        .hasSize(Objects.requireNonNull(db, dbInitializationFailure).size());
  }

  @Test
  @DisplayName("non-authorized users should not be able to fetch all events")
  void getAllEvents_NonAuthorized() throws JOSEException {

    var token = getJwtToken("test_member", "NON-AUTHORIZED_MEMBER");

    webClient.get()
        .uri("/api/events")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  @DisplayName("unauthenticated users should not be able to fetch all events")
  void getAllEvents_Unauthenticated() {

    webClient.get()
        .uri("/api/bookings")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
        .exchange()
        .expectStatus().isUnauthorized();
  }

  //endregion

  //region GET EVENT BY ID

  @Test
  @DisplayName("authorized members should be able to get individual event")
  void getEventById_Authenticated() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure).getFirst().getId();

    var token = getJwtToken("test_member", "MEMBER");

    webClient.get()
        .uri("/api/events/{eventId}", eventId)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EventDto.class);

  }

  @Test
  @DisplayName("non-authenticated user should not be able to access individual events")
  void getEventById_NonAuthenticated() {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure).getFirst().getId();

    webClient.get()
        .uri("/api/events/{eventId}", eventId)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("non-authorized user should not be able to access individual events")
  void getEventById_NonAuthorized() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure).getFirst().getId();

    var token = getJwtToken("test_member", "NON_MEMBER");

    webClient.get()
        .uri("/api/events/{eventId}", eventId)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  //endregion

  //region CREATE EVENT

  @Test
  @DisplayName("authorized performers should be able to create event")
  void createEvent_user_authorized_as_performer() throws JOSEException {

    // performer role implies member as well
    var token = getJwtToken("test_performer", "PERFORMER");

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(
            LocalDateTime.now(clock).plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .durationInMinutes(30)
        .availableBookings(20)
        .build();

    var results = webClient.post()
        .uri("/api/events")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(createEvent), EventCreateRequest.class)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(EventDto.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(results);
    EventStatus resultStatus = eventDtoToStatusMapper.apply(results);
    assertNotNull(resultStatus);
    assertEquals(EventStatus.AWAITING, resultStatus, "Event status should be AWAITING");
    assertEquals(Boolean.TRUE, eventRepository.existsById(results.getId()).block());
  }

  @Test
  @DisplayName("authorized non-performers should not be able to create event")
  void createEvent_user_not_authorized_as_performer() throws JOSEException {

    var token = getJwtToken("test_member", "MEMBER");

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(
            LocalDateTime.now(clock).plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .durationInMinutes(60)
        .availableBookings(20)
        .build();

    webClient.post()
        .uri("/api/events")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(createEvent), EventCreateRequest.class)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  @DisplayName("non-authenticated user should not be able to create event")
  void createEvent_user_not_authenticated() {

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(
            LocalDateTime.now(clock).plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .availableBookings(20)
        .build();

    webClient.post()
        .uri("/api/events")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(Mono.just(createEvent), EventCreateRequest.class)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  //endregion

  //region UPDATE EVENT

  @Test
  @DisplayName("non-performers/owners cannot update event")
  void updateEvent_non_authorized() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("non_performer", "MEMBER");
    var updateRequest = EventUpdateRequest.builder()
        .title("Test Title - Updated")
        .description("Test Description - Updated")
        .location("Test Location - Updated")
        .availableBookings(99)
        .eventDateTime(event.getEventDateTime().plusHours(12))
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  @DisplayName("non-authenticated user cannot update event")
  void updateEvent_non_authenticated() {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var updateRequest = EventUpdateRequest.builder()
        .title("Test Title - Updated")
        .description("Test Description - Updated")
        .location("Test Location - Updated")
        .availableBookings(99)
        .eventDateTime(event.getEventDateTime().plusHours(12))
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("authorized performers can update their event with info before cutoff window")
  void updateEvent_authorized_performer_good_values() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .title("Test Title - Updated")
        .description("Test Description - Updated")
        .location("Test Location - Updated")
        .availableBookings(99)
        .eventDateTime(event.getEventDateTime().plusHours(12))
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EventDto.class);
  }

  @Test
  @DisplayName("authorized performers can update their event's event-date-time before the cutoff window prior to the event starting")
  void updateEvent_authorized_performer_update_event_date_time() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(event.getEventDateTime().plusMinutes(10)) // running a bit late...
        .build();

    var result = webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EventDto.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(result);
    assertEquals(EventStatus.AWAITING, eventDtoToStatusMapper.apply(result));
  }


  @Test
  @DisplayName("authorized performers cannot update their event's event-date-time if event after cutoff point")
  void updateEvent_authorized_performer_update_event_date_time_in_progress() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.IN_PROGRESS))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status IN_PROGRESS"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(event.getEventDateTime().plusMinutes(5))
        .build();

    when(clock.instant()).thenReturn(clockInstant);

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("authorized performer cannot update their event's event-date-time if event is completed")
  void updateEvent_authorized_performer_update_values_while_complete() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.COMPLETED))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status COMPLETED"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(event.getEventDateTime().minusHours(5))
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("authorized PERFORMER cannot update event by setting the date in the past")
  void updateEvent_authorized_PERFORMER_awaiting_to_in_progress_update_cost() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(LocalDateTime.now(clock).minusMinutes(10))
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("authorized PERFORMER cannot update event available bookings if also updating awaiting to in progress")
  void updateEvent_authorized_PERFORMER_awaiting_to_in_progress_update_available_bookings()
      throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(LocalDateTime.now(clock).minusMinutes(2))
        .availableBookings(99)
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("authorized PERFORMER cannot update event location if also updating awaiting to in progress")
  void updateEvent_authorized_PERFORMER_awaiting_to_in_progress_update_location()
      throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(LocalDateTime.now(clock).minusMinutes(2))
        .location("Somewhere in the boonies")
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("authorized PERFORMER cannot update event date-time if also updating awaiting to in progress")
  void updateEvent_authorized_performer_awaiting_to_in_progress_update_event_date_time()
      throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_performer", "PERFORMER");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(LocalDateTime.now(clock).minusMinutes(2))
        .location("Somewhere in the boonies")
        .build();

    webClient.put()
        .uri("/api/events/{eventId}", event.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .body(Mono.just(updateRequest), EventUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
  //endregion

  //region CANCEL EVENT

  @Test
  @DisplayName("authorized performer can cancel event if it hasn't passed it's cutoff window")
  void cancelEvent_Authorized_Performer_Event_Not_Started() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    var token = getJwtToken("test_performer", "PERFORMER");

    var result = webClient.patch()
        .uri("/api/events/{eventId}/cancel", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EventDto.class)
        .returnResult()
        .getResponseBody();

    assertTrue(Objects.requireNonNull(result).isCancelled());
  }

  @Test
  @DisplayName("authorized performer cannot cancel event if it is in progress or ended")
  void cancelEvent_Authorized_Performer_Event_Already_Started() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(event -> EventStatus.IN_PROGRESS == eventDtoToStatusMapper.apply(
            eventMapper.toDto(event)))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    // PERFORMER role has MEMBER, PERFORMER, and PERFORMER authorities
    var token = getJwtToken("test_performer", "PERFORMER");

    webClient.patch()
        .uri("/api/events/{eventId}/cancel", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isBadRequest();

  }

  @Test
  @DisplayName("authorized non-performer cannot cancel event")
  void cancelEvent_NonAuthorized() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(event -> EventStatus.IN_PROGRESS == eventDtoToStatusMapper.apply(
            eventMapper.toDto(event)))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    // PERFORMER role has MEMBER, PERFORMER, and PERFORMER authorities
    var token = getJwtToken("test_member", "MEMBER");

    webClient.patch()
        .uri("/api/events/{eventId}/cancel", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  @DisplayName("non-authenticated user cannot cancel event")
  void cancelEvent_NonAuthenticated() {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, dbInitializationFailure)
        .stream()
        .filter(event -> EventStatus.IN_PROGRESS == eventDtoToStatusMapper.apply(
            eventMapper.toDto(event)))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    webClient.delete()
        .uri("/api/events/{eventId}", eventId)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  //endregion CANCEL EVENT

  //region Helper Methods

  Mono<List<Event>> initializeDatabase() {

    return eventRepository.saveAll(List.of(
        Event.builder() // AWAITING
            .title("Test Event 1")
            .description("Test Event 1")
            .facilitator("test_performer")
            .location("Test Location 1")
            .eventDateTime(LocalDateTime.now(clock).plusDays(2).plusHours(2))
            .durationInMinutes(30)
            .availableBookings(100)
            .build(),
        Event.builder() // IN_PROGRESS
            .title("Test Event 2")
            .description("Test Event 2")
            .facilitator("test_performer")
            .location("Test Location 2")
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(2))
            .durationInMinutes(30)
            .availableBookings(100)
            .build(),
        Event.builder()  // COMPLETED
            .title("Test Event 3")
            .description("Test Event 3")
            .facilitator("test_performer")
            .location("Test Location 3")
            .eventDateTime(LocalDateTime.now(clock).minusDays(2))
            .durationInMinutes(30)
            .availableBookings(100)
            .build()
    )).collectList();
  }

  ///  Generate Token based of RSA Key returned by Wire-mocked OAuth2 server
  String getJwtToken(String username, String... authorities) throws JOSEException {

    var iat = Instant.now(clock);
    var auth_time = iat.plusSeconds(2);
    var exp = iat.plus(3, ChronoUnit.DAYS);
    var issuer = String.format("%s/realms/%s", keycloakServer.baseUrl(), realm);
    var email = String.format("%s@example.com", username);
    var resourceAccess = Map.of(
        oauthClientId, Map.of("roles", Arrays.asList(authorities)),
        "account",
        Map.of("roles", Arrays.asList("manage-account", "manage-account-links", "view-profile"))
    );

    var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
        .type(JOSEObjectType.JWT)
        .keyID(rsaKey.getKeyID())
        .build();

    var payload = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(List.of("account"))
        .subject(username)
        .issueTime(Date.from(iat))
        .expirationTime(Date.from(exp))
        .claim("preferred_username", username)
        .claim("email", email)
        .claim("email_verified", false)
        .claim("name", "Test User")
        .claim("given_name", "Test")
        .claim("family_name", "User")
        .claim("auth_time", Date.from(auth_time))
        .claim("type", "Bearer")
        .claim("realm_access", Map.of("roles", Arrays.asList(authorities)))
        .claim("scope", "openid profile email")
        .claim("resource_access", resourceAccess)
        .claim("azp", oauthClientId)
        .build();

    var signedJwt = new SignedJWT(header, payload);
    signedJwt.sign(new RSASSASigner(rsaKey));
    return signedJwt.serialize();
  }

  void setupKeyCloakServer()
      throws JOSEException, JsonProcessingException {

    if (!keycloakServerInitialized) {

      rsaKey = new RSAKeyGenerator(2048)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(new Algorithm("RS256"))
          .keyID(UUID.randomUUID().toString())
          .generate();

      String jwkPath = java.lang.String.format("/realms/%s/protocol/openid-connect/certs", realm);

      JWKSet jwkSet = new JWKSet(rsaKey);

      keycloakServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(jwkPath))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(objectMapper.writeValueAsString(jwkSet.toJSONObject())
              )
          ));
    }
    keycloakServerInitialized = true;
  }

  boolean eventStatusMatches(Event event, EventStatus expectedStatus) {
    return expectedStatus == eventDtoToStatusMapper.apply(eventMapper.toDto(event));
  }

  //endregion Helper Methods

  //region TestConfig

  @TestConfiguration
  @ActiveProfiles({"test"})
  @Slf4j
  public static class TestIntegrationConfiguration {

    ///  Initializes database structure from schema
    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(
        ConnectionFactory connectionFactory) {
      ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
      initializer.setConnectionFactory(connectionFactory);
      CompositeDatabasePopulator populater = new CompositeDatabasePopulator();
      populater.addPopulators(new ResourceDatabasePopulator(new ClassPathResource(
          "schema.sql")));
      initializer.setDatabasePopulator(populater);
      return initializer;
    }
  }

  //endregion

}

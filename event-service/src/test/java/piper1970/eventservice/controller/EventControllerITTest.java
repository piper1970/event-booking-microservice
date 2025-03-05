package piper1970.eventservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.EventCreateRequest;
import piper1970.eventservice.dto.EventUpdateRequest;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(expression = "#{environment.acceptsProfiles('integration')}", loadContext = true)
//@ActiveProfiles("integration")
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnableWireMock({
    @ConfigureWireMock(
        name = "keystore-service",
        baseUrlProperties = "keystore-service.url")
})
class EventControllerITTest {

  private static RSAKey rsaKey;

  private static final String DB_INITIALIZATION_FAILURE = "Database failed to initialize for testing";

  @Autowired
  private ObjectMapper objectMapper;

  @InjectWireMock("keystore-service")
  WireMockServer keycloakServer;
  boolean keycloakServerInitialized;

  @LocalServerPort
  Integer port;

  @Value("${oauth2.realm}")
  String realm;

  @Value("${oauth2.client.id}")
  String oauthClientId;

  @Autowired
  EventRepository eventRepository;

  WebTestClient webClient;

  @BeforeEach
  void setUp() throws JOSEException, JsonProcessingException {
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
  @DisplayName("authorized users should be able to fetch all events")
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
        .hasSize(Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE).size());
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
  void getAllEvents_Unauthenticated() throws JOSEException {

    webClient.get()
        .uri("/api/bookings")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
        })
        .exchange()
        .expectStatus().isUnauthorized();
  }

  //endregion

  //region GET EVENT BY ID

  @Test
  @DisplayName("authenticated user should be able to get individual event")
  void getEventById_Authenticated() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE).getFirst().getId();

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
  void getEventById_NonAuthenticated(){
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE).getFirst().getId();

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

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE).getFirst().getId();

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
  @DisplayName("authorized (performer) user should be able to create event")
  void createEvent_user_authorized_as_performer() throws JOSEException {

    // performer role implies member as well
    var token = getJwtToken("test_performer", "MEMBER", "PERFORMER");

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(LocalDateTime.now().plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .availableBookings(20)
        .cost(BigDecimal.valueOf(20))
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
    assertEquals(EventStatus.IN_PROGRESS.name(), results.getEventStatus(), "Event status should be IN_PROGRESS");
    assertEquals(Boolean.TRUE, eventRepository.existsById(results.getId()).block());
  }

  @Test
  @DisplayName("non-authorized (performer) user should not be able to create event")
  void createEvent_user_not_authorized_as_performer() throws JOSEException {

    var token = getJwtToken("test_member", "MEMBER");

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(LocalDateTime.now().plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .availableBookings(20)
        .cost(BigDecimal.valueOf(20))
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
  void createEvent_user_not_authenticated(){

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(LocalDateTime.now().plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .availableBookings(20)
        .cost(BigDecimal.valueOf(20))
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
  @DisplayName("authorized admin can update event with update that makes sense")
  void updateEvent_authorized_admin_good_values() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .title("Test Title - Updated")
        .description("Test Description - Updated")
        .location("Test Location - Updated")
        .availableBookings(99)
        .cost(BigDecimal.valueOf(27.27))
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
  @DisplayName("non-authorized user cannot update event")
  void updateEvent_non_authorized() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("non_admin", "PERFORMER", "MEMBER");
    var updateRequest = EventUpdateRequest.builder()
        .title("Test Title - Updated")
        .description("Test Description - Updated")
        .location("Test Location - Updated")
        .availableBookings(99)
        .cost(BigDecimal.valueOf(27.27))
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
  void updateEvent_non_authenticated(){
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var updateRequest = EventUpdateRequest.builder()
        .title("Test Title - Updated")
        .description("Test Description - Updated")
        .location("Test Location - Updated")
        .availableBookings(99)
        .cost(BigDecimal.valueOf(27.27))
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
  @DisplayName("authorized admin can update event status from awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.IN_PROGRESS.name())
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
    assertEquals(EventStatus.IN_PROGRESS.name(), result.getEventStatus());
  }

  @Test
  @DisplayName("authorized admin can update event status from in progress to in complete")
  void updateEvent_authorized_admin_in_progress_to_completed() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.IN_PROGRESS))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status IN_PROGRESS"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.COMPLETED.name())
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
    assertEquals(EventStatus.COMPLETED.name(), result.getEventStatus());
  }

  @Test
  @DisplayName("authorized admin cannot update event status from awaiting to in completed")
  void updateEvent_authorized_admin_awaiting_to_completed() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.COMPLETED.name())
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
  @DisplayName("authorized admin cannot update event status from in progress to awaiting")
  void updateEvent_authorized_admin_in_progress_to_awaiting() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.IN_PROGRESS))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status IN_PROGRESS"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.AWAITING.name())
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
  @DisplayName("authorized admin cannot update event status from in completed to awaiting")
  void updateEvent_authorized_admin_completed_to_awaiting() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.COMPLETED))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status COMPLETED"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.AWAITING.name())
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
  @DisplayName("authorized admin cannot update event status from in completed to in progress")
  void updateEvent_authorized_admin_completed_to_in_progress() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.COMPLETED))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status COMPLETED"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.IN_PROGRESS.name())
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
  @DisplayName("authorized admin cannot update event cost if also updating awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_cost() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.IN_PROGRESS.name())
        .cost(BigDecimal.TEN)
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
  @DisplayName("authorized admin cannot update event available bookings if also updating awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_available_bookings() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.IN_PROGRESS.name())
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
  @DisplayName("authorized admin cannot update event location if also updating awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_location() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.IN_PROGRESS.name())
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
  @DisplayName("authorized admin cannot update event date-time if also updating awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_event_date_time() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> evt.getEventStatus().equals(EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventStatus(EventStatus.IN_PROGRESS.name())
        .eventDateTime(event.getEventDateTime().plusHours(1))
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

  //region DELETE EVENT

  @Test
  @DisplayName("authorized (admin) user can delete event if it hasn't started yet")
  void deleteEvent_Authorized_Admin_Event_Not_Started() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> event.getEventStatus().equals(EventStatus.AWAITING))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    // admin role has MEMBER, PERFORMER, and ADMIN authorities
    var token = getJwtToken("test_admin", "MEMBER", "PERFORMER", "ADMIN");

    webClient.delete()
        .uri("/api/events/{eventId}", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isNoContent();

    assertEquals(Boolean.FALSE, eventRepository.existsById(eventId).block());
  }

  @Test
  @DisplayName("authorized (admin) user can delete event if it has been completed")
  void deleteEvent_Authorized_Admin_Event_Completed() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> event.getEventStatus().equals(EventStatus.COMPLETED))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    // admin role has MEMBER, PERFORMER, and ADMIN authorities
    var token = getJwtToken("test_admin", "MEMBER", "PERFORMER", "ADMIN");

    webClient.delete()
        .uri("/api/events/{eventId}", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isNoContent();

    assertEquals(Boolean.FALSE, eventRepository.existsById(eventId).block());
  }


  @Test
  @DisplayName("authorized (admin) user cannot delete event if it is in progress")
  void deleteEvent_Authorized_Admin_Event_Already_Started() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> event.getEventStatus().equals(EventStatus.IN_PROGRESS))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    // admin role has MEMBER, PERFORMER, and ADMIN authorities
    var token = getJwtToken("test_admin", "MEMBER", "PERFORMER", "ADMIN");

    webClient.delete()
        .uri("/api/events/{eventId}", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isBadRequest();

  }

  @Test
  @DisplayName("non-authorized (non-admin) user cannot delete event")
  void deleteEvent_NonAuthorized() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> event.getEventStatus().equals(EventStatus.IN_PROGRESS))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    // admin role has MEMBER, PERFORMER, and ADMIN authorities
    var token = getJwtToken("test_performer", "MEMBER", "PERFORMER");

    webClient.delete()
        .uri("/api/events/{eventId}", eventId)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  @DisplayName("non-authenticated usercannot delete event")
  void deleteEvent_NonAuthenticated(){
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> event.getEventStatus().equals(EventStatus.IN_PROGRESS))
        .map(Event::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));

    webClient.delete()
        .uri("/api/events/{eventId}", eventId)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  //endregion

  //region Helper Methods

  /// Initialize RSA Key and set mock oauth2 server to return it when prompted
  private void setupKeyCloakServer()
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

  private Mono<List<Event>> initializeDatabase() {
    return eventRepository.saveAll(List.of(
        Event.builder()
            .title("Test Event 1")
            .description("Test Event 1")
            .facilitator("test_performer")
            .location("Test Location 1")
            .eventDateTime(LocalDateTime.now().plusDays(2).plusHours(2))
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .eventStatus(EventStatus.AWAITING)
            .build(),
        Event.builder()
            .title("Test Event 2")
            .description("Test Event 2")
            .facilitator("test_performer")
            .location("Test Location 2")
            .eventDateTime(LocalDateTime.now().minusHours(2))
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .eventStatus(EventStatus.IN_PROGRESS)
            .build(),
        Event.builder()
            .title("Test Event 3")
            .description("Test Event 3")
            .facilitator("test_performer")
            .location("Test Location 3")
            .eventDateTime(LocalDateTime.now().minusDays(2))
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .eventStatus(EventStatus.COMPLETED)
            .build()
    )).collectList();
  }

  ///  Generate Token based of RSA Key returned by Wire-mocked OAuth2 server
  private String getJwtToken(String username, String... authorities) throws JOSEException {

    var iat = Instant.now();
    var auth_time = iat.plusSeconds(2);
    var exp = iat.plus(10, ChronoUnit.HOURS);
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

  //endregion

  //region TestConfiguration

  @TestConfiguration
  @Profile("integration")
//  @ActiveProfiles("integration")
  public static class TestIntegrationConfiguration {

    ///  Initializes database structure from schema
    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(
        ConnectionFactory connectionFactory) {
      ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
      initializer.setConnectionFactory(connectionFactory);
      CompositeDatabasePopulator populater = new CompositeDatabasePopulator();
      populater.addPopulators(new ResourceDatabasePopulator(new ClassPathResource(
          "schema-integration.sql")));
      initializer.setDatabasePopulator(populater);
      return initializer;
    }
  }

  //endregion

}

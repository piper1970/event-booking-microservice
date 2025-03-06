package piper1970.eventservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.JOSEException;
import io.r2dbc.spi.ConnectionFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.testcontainers.junit.jupiter.Testcontainers;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.EventCreateRequest;
import piper1970.eventservice.dto.EventUpdateRequest;
import reactor.core.publisher.Mono;

@EnabledIf(expression = "#{environment.acceptsProfiles('integration')}", loadContext = true)
//@ActiveProfiles("integration")
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
class EventControllerITTests extends EventControllerTestsBase {

  @Test
  @DisplayName("authorized users should be able to fetch all events")
  void getAllEvents() throws JOSEException {

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
  @DisplayName("authorized users should be able to get individual event")
  void getEventById() throws JOSEException {
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
  @DisplayName("authorized (performer) users should be able to create event")
  void createEvent_user_authorized_as_performer() throws JOSEException {

    // performer role implies member as well
    var token = getJwtToken("test_performer", "MEMBER", "PERFORMER");

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(LocalDateTime.now(clock).plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .durationInMinutes(30)
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
    EventStatus resultStatus = eventDtoToStatusMapper.apply(results);
    assertNotNull(resultStatus);
    assertEquals(EventStatus.AWAITING, resultStatus, "Event status should be AWAITING");
    assertEquals(Boolean.TRUE, eventRepository.existsById(results.getId()).block());
  }

  @Test
  @DisplayName("authorized admin can update event info before cutoff window prior to the event starting")
  void updateEvent_authorized_admin_good_values() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
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
  @DisplayName("authorized (admin) user can delete event if it hasn't started yet")
  void deleteEvent_Authorized_Admin_Event_Not_Started() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(event)))
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


}

package piper1970.eventservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.EventCreateRequest;
import piper1970.eventservice.dto.EventUpdateRequest;
import reactor.core.publisher.Mono;

public class EventControllerTests extends EventControllerTestsBase {

  @Value("${event.change.cutoff.minutes}")
  private Integer cutoffPoint;

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
  void getAllEvents_Unauthenticated(){

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
  void getEventById_NonAuthenticated() {
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
  @DisplayName("non-authorized (performer) user should not be able to create event")
  void createEvent_user_not_authorized_as_performer() throws JOSEException {

    var token = getJwtToken("test_member", "MEMBER");

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(LocalDateTime.now(clock).plusDays(2).withHour(13).withMinute(0).withSecond(0))
        .durationInMinutes(60)
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
  void createEvent_user_not_authenticated() {

    var createEvent = EventCreateRequest.
        builder()
        .title("Test Title")
        .description("Test Description")
        .location("Test Location")
        .eventDateTime(LocalDateTime.now(clock).plusDays(2).withHour(13).withMinute(0).withSecond(0))
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
  @DisplayName("non-authorized user cannot update event")
  void updateEvent_non_authorized() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
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
  void updateEvent_non_authenticated() {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
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
  @DisplayName("authorized admin can update event with non-temporal info before cutoff window prior to the event starting")
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
  @DisplayName("authorized admin can update event-date-time before the cutoff window prior to the event starting")
  void updateEvent_authorized_admin_update_event_date_time() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.AWAITING))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
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
  @DisplayName("authorized admin cannot update event-date-time if event after cutoff point")
  void updateEvent_authorized_admin_update_event_date_time_in_progress() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.IN_PROGRESS))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status IN_PROGRESS"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(event.getEventDateTime().plusMinutes(5))
        .build();

    // manually adjust time
    long cutoffPointToSeconds = cutoffPoint.longValue() * 60;
    Instant newInstant = CLOCK_INSTANT.minusSeconds(cutoffPointToSeconds);
    when(clock.instant()).thenReturn(newInstant);

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
  @DisplayName("authorized admin cannot update event-date-time if event is completed")
  void updateEvent_authorized_admin_update_values_while_complete() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> eventStatusMatches(evt, EventStatus.COMPLETED))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status COMPLETED"));
    var token = getJwtToken("test_admin", "ADMIN");
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
  @DisplayName("authorized admin cannot update event by setting the date in the past")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_cost() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
    var updateRequest = EventUpdateRequest.builder()
        .eventDateTime(LocalDateTime.now(clock).minusMinutes(10))
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
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_available_bookings()
      throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
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
  @DisplayName("authorized admin cannot update event location if also updating awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_location() throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
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
  @DisplayName("authorized admin cannot update event date-time if also updating awaiting to in progress")
  void updateEvent_authorized_admin_awaiting_to_in_progress_update_event_date_time()
      throws JOSEException {
    var db = initializeDatabase()
        .block();
    var event = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("No event found with status AWAITING"));
    var token = getJwtToken("test_admin", "ADMIN");
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

  //region DELETE EVENT

  @Test
  @DisplayName("authorized (admin) user can delete event if it hasn't started yet")
  void deleteEvent_Authorized_Admin_Event_Not_Started() throws JOSEException {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(evt -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(eventMapper.toDto(evt)))
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
        .filter(event -> EventStatus.COMPLETED == eventDtoToStatusMapper.apply(eventMapper.toDto(event)))
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
        .filter(event -> EventStatus.IN_PROGRESS == eventDtoToStatusMapper.apply(eventMapper.toDto(event)))
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
        .filter(event -> EventStatus.IN_PROGRESS == eventDtoToStatusMapper.apply(eventMapper.toDto(event)))
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
  void deleteEvent_NonAuthenticated() {
    var db = initializeDatabase()
        .block();

    var eventId = Objects.requireNonNull(db, DB_INITIALIZATION_FAILURE)
        .stream()
        .filter(event -> EventStatus.IN_PROGRESS == eventDtoToStatusMapper.apply(eventMapper.toDto(event)))
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

}

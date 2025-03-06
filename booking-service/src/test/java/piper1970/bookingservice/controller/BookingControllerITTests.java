package piper1970.bookingservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import io.r2dbc.spi.ConnectionFactory;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import reactor.core.publisher.Mono;

@EnabledIf(expression = "#{environment.acceptsProfiles('integration')}", loadContext = true)
//@ActiveProfiles("integration")
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@DisplayName("Booking Controller w/Postgres TestContainer")
class BookingControllerITTests extends BookingControllerTestsBase {


  //region GET ALL BOOKINGS

  @Test
  @DisplayName("authorized members should be able to retrieve all their bookings")
  void getAllBookings_Authenticated_And_Authorized_Owner_Of_Bookings() throws JOSEException {
    //add bookings to the repo

    initializeDatabase()
        .then()
        .block();

    var token = getJwtToken("test_member", "MEMBER");

    webClient.get()
        .uri("/api/bookings")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(BookingDto.class)
        .hasSize(2);
  }

  @Test
  @DisplayName("authorized admins should be able to retrieve all bookings from all users")
  void getAllBookings_Authenticated_And_Authorized_As_Admin() throws JOSEException {
    //add bookings to the repo

    var books = initializeDatabase()
        .block();

    // ADMIN implies MEMBER
    var token = getJwtToken("non_test_member", "ADMIN", "MEMBER");

    webClient.get()
        .uri("/api/bookings")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isOk()
        .expectBodyList(BookingDto.class)
        .hasSize(Objects.requireNonNull(books, DB_INITIALIZATION_FAILURE).size());
  }

  //endregion

  //region GET BOOKING BY ID

  @Test
  @DisplayName("authorized members should be able to retrieve a booking made by that user")
  void getBookingById_Authenticated_Authorized_Owner() throws JOSEException {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings, DB_INITIALIZATION_FAILURE).stream()
        .filter(booking -> booking.getUsername().equals("test_member"))
        .map(Booking::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found in database"));

    var token = getJwtToken("test_member", "MEMBER");

    webClient.get()
        .uri("/api/bookings/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(BookingDto.class);
  }

  @Test
  @DisplayName("authorized admins should be able to retrieve any booking from any users")
  void getBookingById_Authenticated_Authorized_Admin() throws JOSEException {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings, DB_INITIALIZATION_FAILURE).stream()
        .filter(Predicate.not(booking -> booking.getUsername().equals("test_member")))
        .map(Booking::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "MEMBER", "ADMIN");

    webClient.get()
        .uri("/api/bookings/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isOk()
        .expectBody(BookingDto.class);
  }


  //endregion

  //region CREATE BOOKING

  @Test
  @DisplayName("authorized member should be able to create booking for upcoming event")
  void createBooking_user_authorized_event_still_upcoming()
      throws JsonProcessingException, JOSEException {
    var eventId = 1;
    var createRequest = BookingCreateRequest.builder()
        .eventId(eventId)
        .build();
    var token = getJwtToken("test_member", "MEMBER");

    // mock event server
    mockEventServer(eventId, 50,
        LocalDateTime.now().plusHours(5), token);

    var results = webClient.post()
        .uri("/api/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers ->
            headers.setBearerAuth(token)
        ).accept(MediaType.APPLICATION_JSON)
        .body(Mono.just(createRequest), BookingCreateRequest.class)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(BookingDto.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(results);
    assertEquals(BookingStatus.IN_PROGRESS.name(), results.getBookingStatus(), "Booking status should be IN_PROGRESS");
    assertEquals(Boolean.TRUE, bookingRepository.existsById(results.getId()).block());
  }

  @Test
  @DisplayName("authorized non-members should not be able to create booking")
  void createBooking_user_non_authorized() throws JOSEException {
    var eventId = 5;
    var createRequest = BookingCreateRequest.builder()
        .eventId(eventId)
        .build();
    var token = getJwtToken("test_member", "NON_MEMBER");

    webClient.post()
        .uri("/api/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers ->
            headers.setBearerAuth(token)
        )
        .body(Mono.just(createRequest), BookingCreateRequest.class)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  //endregion

  //region DELETE BOOKING

  @Test
  @DisplayName("authorized admins should be able to delete a booking if booking event has not yet started")
  void deleteBooking_Authenticated_Admin_Event_Still_Waiting()
      throws JOSEException, JsonProcessingException {

    var bookings = initializeDatabase()
        .block();

    var booking = Objects.requireNonNull(bookings).stream()
        .filter(bkg -> bkg.getUsername().equals("test_member"))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "MEMBER", "ADMIN");

    mockEventServer(booking.getEventId(), 50,
        LocalDateTime.now().plusHours(5), token);

    webClient.delete()
        .uri("/api/bookings/{id}", booking.getId())
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isNoContent();

    assertEquals(Boolean.FALSE, bookingRepository.existsById(booking.getId()).block());
  }

  @Test
  @DisplayName("authorized non-admins should not be able to delete a booking")
  void deleteBooking_Authenticated_Non_Admin() throws JOSEException {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings).stream()
        .filter(booking -> booking.getUsername().equals("test_member"))
        .map(Booking::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "MEMBER", "PERFORMER");

    webClient.delete()
        .uri("/api/bookings/{id}", id)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  //endregion

  //region TestConfig

  @TestConfiguration
  @Profile("integration")
//  @ActiveProfiles("integration")
  @Slf4j
  public static class TestIntegrationConfiguration {

    private final String apiUri;

    public TestIntegrationConfiguration(@Value("${api.event-service.uri}") String apiUri) {
      this.apiUri = apiUri;
    }

    ///  Initializes database structure from schema
    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(
        ConnectionFactory connectionFactory) {
      ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
      initializer.setConnectionFactory(connectionFactory);
      CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
      populator.addPopulators(new ResourceDatabasePopulator(new ClassPathResource(
          "schema-integration.sql")));
      initializer.setDatabasePopulator(populator);
      return initializer;
    }

    ///  Need to override webClientBuilder to disable @LoadBalanced behavior
    /// spring.main.allow-bean-definition-overriding=true added to
    /// application-integration.properties to ensure this works
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {

      return WebClient.builder()
          .baseUrl(apiUri);
    }
  }

  //endregion

}
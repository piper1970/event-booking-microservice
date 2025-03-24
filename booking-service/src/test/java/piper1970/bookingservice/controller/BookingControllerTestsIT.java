package piper1970.bookingservice.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;

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
import java.io.File;
import java.nio.file.Paths;
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
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.dto.EventDto;
import reactor.core.publisher.Mono;

@DisplayName("Booking Controller")
@ActiveProfiles({"test", "integration"})
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({
    @ConfigureWireMock(
        name = "event-service",
        baseUrlProperties = "event-service.url"
    ),
    @ConfigureWireMock(
        name = "keystore-service",
        baseUrlProperties = "keystore-service.url")
})
@Tag("integration-test")
@Slf4j
public class BookingControllerTestsIT {

  //region Setup Properties

  @Container
  static ComposeContainer composeContainer = createComposeContainer();

  static RSAKey rsaKey;

  final Instant clockInstant = Instant.now();
  final ZoneId clockZone = ZoneId.systemDefault();
  final String dbInitializationFailure = "Database failed to initialize for testing";

  @Autowired
  ObjectMapper objectMapper;

  @InjectWireMock("keystore-service")
  WireMockServer keycloakServer;
  boolean keycloakServerInitialized;

  @InjectWireMock("event-service")
  WireMockServer eventsServer;

  @Autowired
  BookingRepository bookingRepository;

  @MockitoBean
  Clock clock;

  @LocalServerPort
  Integer port;

  @Value("${oauth2.realm}")
  String realm;

  @Value("${oauth2.client.id}")
  String oauthClientId;

  WebTestClient webClient;

  //endregion Setup Properties

  //region Before/After Setup

  @BeforeAll
  static void setup() {
    composeContainer.start();
  }

  @AfterAll
  static void tearDown() {
    composeContainer.stop();
  }

  @BeforeEach
  void setUp() throws JOSEException, JsonProcessingException {

    given(clock.instant()).willReturn(clockInstant);
    given(clock.getZone()).willReturn(clockZone);

    webClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
    bookingRepository.deleteAll()
        .then()
        .block();
    setupKeyCloakServer();
  }

  //endregion Before/After Setup

  //region GET ALL BOOKINGS

  @Test
  @DisplayName("authorized users should be able to retrieve all their bookings")
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
  @DisplayName("authorized users (non-admin) should not be able to retrieve bookings from other users")
  void getAllBookings_Authenticated_And_Authorized_Not_Owner_Of_Bookings() throws JOSEException {
    //add bookings to the repo

    initializeDatabase()
        .then()
        .block();

    var token = getJwtToken("non_test_member", "MEMBER");

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
        .hasSize(0);
  }

  @Test
  @DisplayName("non-authenticated users should not be able to retrieve bookings")
  void getAllBookings_Not_Authenticated() {

    webClient.get()
        .uri("/api/bookings")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
        .exchange()
        .expectStatus().isUnauthorized();

  }

  @Test
  @DisplayName("non-authorized users should not be able to retrieve bookings")
  void getAllBookings_Authenticated_And_Not_Authorized() throws JOSEException {

    var token = getJwtToken("test_member", "NON-AUTHORIZED_MEMBER");

    webClient.get()
        .uri("/api/bookings")
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isForbidden();
  }

  //endregion

  //region GET BOOKING BY ID

  @Test
  @DisplayName("authorized user should be able to retrieve a booking made by that user")
  void getBookingById_Authenticated_Authorized_Owner() throws JOSEException {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings, dbInitializationFailure).stream()
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
  @DisplayName("non-authenticated user should not be able to access a booking")
  void getBookingById_Not_Authenticated() {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings, dbInitializationFailure).stream()
        .filter(booking -> booking.getUsername().equals("test_member"))
        .map(Booking::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    webClient.get()
        .uri("/api/bookings/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("authorized user should not be able to access other another users' booking")
  void getBookingById_Auth_Auth_Not_Owner() throws JOSEException {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings, dbInitializationFailure).stream()
        .filter(Predicate.not(booking -> booking.getUsername().equals("test_member")))
        .map(Booking::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "MEMBER");

    webClient.get()
        .uri("/api/bookings/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  @DisplayName("non-authorized user should not be able to access a booking")
  void getBookingById_Not_Authorized() throws JOSEException {

    var bookings = initializeDatabase()
        .block();

    var id = Objects.requireNonNull(bookings, dbInitializationFailure).stream()
        .filter(booking -> booking.getUsername().equals("test_member"))
        .map(Booking::getId)
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "UNMAPPED");

    webClient.get()
        .uri("/api/bookings/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(token);
        })
        .exchange()
        .expectStatus().isForbidden();
  }

  //endregion

  //region CREATE BOOKING

  @Test
  @DisplayName("authorized user should be able to create booking for upcoming event")
  void createBooking_user_authorized_event_still_upcoming()
      throws JsonProcessingException, JOSEException {
    var eventId = 1;
    var createRequest = BookingCreateRequest.builder()
        .eventId(eventId)
        .build();
    var token = getJwtToken("test_member", "MEMBER");

    // mock event server
    mockEventServer(eventId, 50,
        LocalDateTime.now(clock).plusHours(5), token);

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
    assertEquals(BookingStatus.IN_PROGRESS.name(), results.getBookingStatus(),
        "Booking status should be IN_PROGRESS");
    assertEquals(Boolean.TRUE, bookingRepository.existsById(results.getId()).block());
  }

  @Test
  @DisplayName("authorized user should not be able to create booking for event already in progress")
  void createBooking_user_authenticated_and_authorized_event_in_progress()
      throws JsonProcessingException, JOSEException {
    var eventId = 5;
    var createRequest = BookingCreateRequest.builder()
        .eventId(eventId)
        .build();
    var token = getJwtToken("test_member", "MEMBER");

    // mock event server
    mockEventServer(eventId, 50,
        LocalDateTime.now(clock).minusMinutes(5), token);

    webClient.post()
        .uri("/api/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers ->
            headers.setBearerAuth(token)
        ).accept(MediaType.APPLICATION_JSON)
        .body(Mono.just(createRequest), BookingCreateRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("non-authenticated user should not be able to create booking")
  void createBooking_user_non_authenticated() {
    var eventId = 5;
    var createRequest = BookingCreateRequest.builder()
        .eventId(eventId)
        .build();

    webClient.post()
        .uri("/api/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(Mono.just(createRequest), BookingCreateRequest.class)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("non-authorized user should not be able to create booking")
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

  @Test
  @DisplayName("authorized user should not be able to create booking for upcoming event if event has to spots left")
  void createBooking_user_authorized_no_bookings_left()
      throws JOSEException, JsonProcessingException {
    var eventId = 1;
    var createRequest = BookingCreateRequest.builder()
        .eventId(eventId)
        .build();
    var token = getJwtToken("test_member", "MEMBER");

    // mock event server
    mockEventServer(eventId, 0,
        LocalDateTime.now(clock).plusHours(5), token);

    webClient.post()
        .uri("/api/bookings")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers ->
            headers.setBearerAuth(token)
        ).accept(MediaType.APPLICATION_JSON)
        .body(Mono.just(createRequest), BookingCreateRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  //endregion

  //region CANCEL BOOKING

  @Test
  @DisplayName("authorized member and owner of booking should be able to cancel a booking if booking event has not yet started")
  void cancelBooking_Authenticated_Member_Owner_Of_Booking_Event_Still_Waiting()
      throws JOSEException, JsonProcessingException {
    var bookings = initializeDatabase()
        .block();

    var booking = Objects.requireNonNull(bookings).stream()
        .filter(bkg -> bkg.getUsername().equals("test_member"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "MEMBER");

    mockEventServer(booking.getEventId(), 50,
        LocalDateTime.now(clock).plusHours(5), token);

    var results = webClient.patch()
        .uri("/api/bookings/{id}/cancel", booking.getId())
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(BookingDto.class)
        .returnResult()
        .getResponseBody();

    assertEquals(BookingStatus.CANCELLED.name(),
        Objects.requireNonNull(results).getBookingStatus());
  }

  @Test
  @DisplayName("authorized member and owner of booking should not be able to cancel a booking if booking event has started")
  void cancelBooking_Authenticated_Member_Owner_Of_Booking_Event_In_Progress()
      throws JOSEException, JsonProcessingException {
    var bookings = initializeDatabase()
        .block();

    var booking = Objects.requireNonNull(bookings).stream()
        .filter(bkg -> bkg.getUsername().equals("test_member"))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Booking not found"));

    var token = getJwtToken("test_member", "MEMBER");

    mockEventServer(booking.getEventId(), 50,
        LocalDateTime.now(clock).minusMinutes(1), token);

    webClient.patch()
        .uri("/api/bookings/{id}/cancel", booking.getId())
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  //endregion CANCEL BOOKING

  //region Helper Methods

  ///  fill database with default bookings
  Mono<List<Booking>> initializeDatabase() {
    return bookingRepository.saveAll(List.of(
        Booking.builder()
            .eventId(1)
            .username("test_member")
            .email("test_member@example.com")
            .eventDateTime(LocalDateTime.now(clock).plusDays(5))
            .bookingStatus(BookingStatus.IN_PROGRESS)
            .build(),
        Booking.builder()
            .eventId(2)
            .username("test_member")
            .email("test_member@example.com")
            .eventDateTime(LocalDateTime.now(clock).plusDays(6))
            .bookingStatus(BookingStatus.IN_PROGRESS)
            .build(),
        Booking.builder()
            .eventId(1)
            .username("test_member-2")
            .email("test_member-2@example.com")
            .eventDateTime(LocalDateTime.now(clock).plusDays(5))
            .bookingStatus(BookingStatus.IN_PROGRESS)
            .build()
    )).collectList();
  }


  ///  Generate Token based of RSA Key returned by Wire-mocked OAuth2 server
  String getJwtToken(String username, String... authorities) throws JOSEException {

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


  /**
   * Stubs wiremock server to return event-dto based on given parameters. Parameters given - other
   * than id -  are used by calling service as criteria for whether the booking should be allowed to
   * be created.
   *
   * @param eventId                   id for event
   * @param availableBookingsForEvent Number of bookings available
   * @param eventDateTime             date of the event
   * @throws JsonProcessingException if event cannot be marshalled to JSON
   */
  void mockEventServer(
      Integer eventId,
      Integer availableBookingsForEvent,
      LocalDateTime eventDateTime,
      String token
  )
      throws JsonProcessingException {
    var event = EventDto.builder()
        .id(eventId)
        .availableBookings(availableBookingsForEvent)
        .description("Test description")
        .title("Test title")
        .facilitator("Test Facilitator")
        .eventDateTime(eventDateTime)
        .durationInMinutes(60)
        .location("Test location")
        .build();

    String path = "/api/events/" + eventId;

    eventsServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(path))
        .withHeader("Authorization", WireMock.equalTo("Bearer " + token))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(event))
        ));
  }

  /// Initialize RSA Key and set mock oauth2 server to return it when prompted
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

  /// Setup TestContainer based off docker-compose test file
  @SuppressWarnings("all")
  static ComposeContainer createComposeContainer() {
    String userDirectory = System.getProperty("user.dir");
    String filePath = userDirectory + "/docker-compose-tests.yaml";
    var path = Paths.get(filePath).normalize().toAbsolutePath();
    var file = new File(filePath);
    return new ComposeContainer(
        file
    )
        .withLocalCompose(true)
        .withExposedService("postgres-bookings-test", 5432)
        .withExposedService("kafka-bookings-test", 9092);
  }


  //endregion Helper Methods

  //region Test Configuration

  @TestConfiguration
  @ActiveProfiles({"test", "integration"})
  public static class TestControllerConfiguration {

    final String apiUri;

    public TestControllerConfiguration(@Value("${api.event-service.uri}") String apiUri) {
      System.out.println(("Initializing TestControllerConfiguration: ".toUpperCase()));
      this.apiUri = apiUri;
    }

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

    ///  Need to override webClientBuilder to disable @LoadBalanced behavior
    /// spring.main.allow-bean-definition-overriding=true added to
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {

      return WebClient.builder()
          .baseUrl(apiUri);
    }
  }

  //endregion Test Configuration

}

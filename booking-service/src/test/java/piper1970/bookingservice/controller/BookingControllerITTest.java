package piper1970.bookingservice.controller;

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
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(expression = "#{environment.acceptsProfiles('integration')}", loadContext = true)
//@ActiveProfiles("integration")
@Testcontainers
@EnableWireMock({
    @ConfigureWireMock(
        name = "event-service",
        baseUrlProperties = "event-service.url"
    ),
    @ConfigureWireMock(
        name = "keystore-service",
        baseUrlProperties = "keystore-service.url")
})
class BookingControllerITTest {

  // set in BeforeAll class runner
  private static RSAKey rsaKey;

  @Autowired
  private ObjectMapper objectMapper;

  @InjectWireMock("keystore-service")
  WireMockServer keycloakServer;
  boolean keycloakServerInitialized;

  @InjectWireMock("event-service")
  WireMockServer eventsServer;

  @Autowired
  BookingRepository bookingRepository;

  @LocalServerPort
  Integer port;

  @Value("${oauth2.realm}")
  String realm;

  @Value("${oauth2.client.id}")
  String oauthClientId;

  @Value("${api.event-service.path}")
  String eventsPath;

  WebTestClient webClient;

  @BeforeEach
  void setUp() throws JOSEException, JsonProcessingException {

    webClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
    bookingRepository.deleteAll()
        .block();

    mockKeycloakServer(keycloakServer);
  }

  @Test
  @DisplayName("should retrieve all bookings when authorized")
  void getAllBookings_Authenticated_And_Authorized() throws JOSEException {
    //add bookings to the repo
    bookingRepository.saveAll(List.of(
            Booking.builder()
                .eventId(1)
                .username("test_member")
                .eventDateTime(LocalDateTime.now().plusDays(5))
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .build(),
            Booking.builder()
                .eventId(2)
                .username("test_member")
                .eventDateTime(LocalDateTime.now().plusDays(6))
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .build(),
            Booking.builder()
                .eventId(1)
                .username("test_member-2")
                .eventDateTime(LocalDateTime.now().plusDays(5))
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .build()
        ))
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
  @Disabled()
  void getBookingById() {
    fail("Not Yet Implemented...");
  }

  @Test
  @Disabled()
  void createBooking() {
    fail("Not Yet Implemented...");
  }

  @Test
  @Disabled()
  void updateBooking() {
    fail("Not Yet Implemented...");
  }

  @Test
  @Disabled()
  void deleteBooking() {
    fail("Not Yet Implemented...");
  }

  private void mockEventServer(WireMockServer eventsServer, Integer id)
      throws JsonProcessingException {
    var event = EventDto.builder()
        .id(id)
        .availableBookings(100)
        .description("Test")
        .title("Test")
        .facilitator("test_facilitator")
        .eventDateTime(LocalDateTime.now().plusDays(2))
        .eventStatus(EventStatus.IN_PROGRESS.name())
        .location("Test Location")
        .cost(BigDecimal.valueOf(100))
        .build();
    eventsServer.stubFor(WireMock.get(String.format("/%s/%d", eventsPath, id))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(event))
        ));
  }

  private void mockKeycloakServer(WireMockServer keycloakServer)
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

  @TestConfiguration
  @ActiveProfiles("integration")
  public static class TestDatabaseConfiguration {

    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(
        ConnectionFactory connectionFactory) {
      ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
      initializer.setConnectionFactory(connectionFactory);
      CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
      populator.addPopulators(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
      initializer.setDatabasePopulator(populator);
      return initializer;
    }
  }

}
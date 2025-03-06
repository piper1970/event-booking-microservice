package piper1970.bookingservice.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.dto.EventDto;
import reactor.core.publisher.Mono;

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
public abstract class BookingControllerTestsBase {

  protected static RSAKey rsaKey;

  protected static final String DB_INITIALIZATION_FAILURE = "Database failed to initialize for testing";

  @Autowired
  protected ObjectMapper objectMapper;

  @InjectWireMock("keystore-service")
  protected WireMockServer keycloakServer;
  protected boolean keycloakServerInitialized;

  @InjectWireMock("event-service")
  protected WireMockServer eventsServer;

  @Autowired
  protected BookingRepository bookingRepository;

  @LocalServerPort
  protected Integer port;

  @Value("${oauth2.realm}")
  protected String realm;

  @Value("${oauth2.client.id}")
  protected String oauthClientId;

  protected WebTestClient webClient;

  @BeforeEach
  void setUp() throws JOSEException, JsonProcessingException {

    webClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
    bookingRepository.deleteAll()
        .then()
        .block();
    setupKeyCloakServer();
  }

  ///  fill database with default bookings
  protected Mono<List<Booking>> initializeDatabase() {
    return bookingRepository.saveAll(List.of(
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
    )).collectList();
  }


  ///  Generate Token based of RSA Key returned by Wire-mocked OAuth2 server
  protected String getJwtToken(String username, String... authorities) throws JOSEException {

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
  protected void mockEventServer(
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
        .cost(BigDecimal.valueOf(100))
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

}

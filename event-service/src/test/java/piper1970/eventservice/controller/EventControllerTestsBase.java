package piper1970.eventservice.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.mapper.EventMapper;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({
    @ConfigureWireMock(
        name = "keystore-service",
        baseUrlProperties = "keystore-service.url")
})
public abstract class EventControllerTestsBase {

  protected RSAKey rsaKey;

  @LocalServerPort
  Integer port;

  @Autowired
  protected EventMapper eventMapper;

  @MockitoBean
  protected Clock clock;

  @InjectMocks
  protected EventDtoToStatusMapper eventDtoToStatusMapper;

  @Autowired
  protected ObjectMapper objectMapper;

  protected static final Instant CLOCK_INSTANT = Instant.parse("2025-03-05T14:35:00Z");
  protected static final ZoneId CLOCK_ZONE = ZoneId.systemDefault();

  protected static final String DB_INITIALIZATION_FAILURE = "Database failed to initialize for testing";

  @InjectWireMock("keystore-service")
  WireMockServer keycloakServer;
  protected boolean keycloakServerInitialized;

  @Autowired
  protected EventRepository eventRepository;

  @Value("${oauth2.realm}")
  String realm;

  @Value("${oauth2.client.id}")
  String oauthClientId;

  WebTestClient webClient;

  @BeforeEach
  protected void setUp() throws JOSEException, JsonProcessingException {

    given(clock.instant()).willReturn(CLOCK_INSTANT);
    given(clock.getZone()).willReturn(CLOCK_ZONE);

    webClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
    eventRepository.deleteAll()
        .then()
        .block();
    setupKeyCloakServer();
  }

  protected Mono<List<Event>> initializeDatabase() {

    return eventRepository.saveAll(List.of(
        Event.builder() // AWAITING
            .title("Test Event 1")
            .description("Test Event 1")
            .facilitator("test_performer")
            .location("Test Location 1")
            .eventDateTime(LocalDateTime.now(clock).plusDays(2).plusHours(2))
            .durationInMinutes(30)
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .build(),
        Event.builder() // IN_PROGRESS
            .title("Test Event 2")
            .description("Test Event 2")
            .facilitator("test_performer")
            .location("Test Location 2")
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(2))
            .durationInMinutes(30)
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .build(),
        Event.builder()  // COMPLETED
            .title("Test Event 3")
            .description("Test Event 3")
            .facilitator("test_performer")
            .location("Test Location 3")
            .eventDateTime(LocalDateTime.now(clock).minusDays(2))
            .durationInMinutes(30)
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .build()
    )).collectList();
  }

  ///  Generate Token based of RSA Key returned by Wire-mocked OAuth2 server
  protected String getJwtToken(String username, String... authorities) throws JOSEException {

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

  protected boolean eventStatusMatches(Event event, EventStatus expectedStatus) {
    return expectedStatus == eventDtoToStatusMapper.apply(eventMapper.toDto(event));
  }

}

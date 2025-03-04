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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(expression = "#{environment.acceptsProfiles('integration')}", loadContext = true)
@ActiveProfiles("integration")
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnableWireMock({
    @ConfigureWireMock(
        name = "keystore-service",
        baseUrlProperties = "keystore-service.url")
})
class EventControllerITTest {

  private static RSAKey rsaKey;

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
        .hasSize(Objects.requireNonNull(db).size());
  }
  //endregion

  //region GET EVENT BY ID
  //endregion

  //region CREATE EVENT
  //endregion

  //region UPDATE EVENT
  //endregion

  //region DELETE EVENT
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
            .build(),
        Event.builder()
            .title("Test Event 4")
            .description("Test Event 4")
            .facilitator("test_performer")
            .location("Test Location 4")
            .eventDateTime(LocalDateTime.now().plusDays(2))
            .cost(BigDecimal.valueOf(100))
            .availableBookings(100)
            .eventStatus(EventStatus.CANCELLED)
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
  @ActiveProfiles("integration")
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

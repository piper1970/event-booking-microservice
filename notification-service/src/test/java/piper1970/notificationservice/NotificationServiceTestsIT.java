package piper1970.notificationservice;

import static org.mockito.BDDMockito.given;

import io.r2dbc.spi.ConnectionFactory;
import java.io.File;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.publisher.Mono;

@DisplayName("Notification Service Integration Test")
@ActiveProfiles({"test", "integration_test_containers"})
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration-test")
public class NotificationServiceTestsIT {

  //region Properties Setup

  @Autowired
  RouterFunction<ServerResponse> route;

  @Container
  static ComposeContainer composeContainer = createComposeContainer();

  @MockitoBean
  Clock clock;

  final Instant clockInstant = Instant.now();
  final ZoneId clockZone = ZoneId.systemDefault();
  final String apiEndpoint = "/api/notifications/confirm/{confirmationString}";

  final UUID goodConfirmationToken = UUID.randomUUID();
  final UUID expiredConfirmationToken = UUID.randomUUID();

  @Autowired
  BookingConfirmationRepository bookingConfirmationRepository;

  WebTestClient webClient;

  //endregion Properties Setup

  //region Before/After Setup

  @BeforeAll
  static void beforeAll() {
    composeContainer.start();
  }

  @AfterAll
  static void afterAll() {
    composeContainer.stop();
  }

  @BeforeEach
  void setup() {
    given(clock.instant()).willReturn(clockInstant);
    given(clock.getZone()).willReturn(clockZone);

    webClient = WebTestClient.bindToRouterFunction(route).build();
  }

  //endregion Before/After Setup

  //region ConfirmEvent Test Scenarios

  @Test
  @DisplayName("should return a BAD_REQUEST if token is expired")
  void confirmBooking_expired(){

    initializeDatabase()
        .block();

    webClient.get()
        .uri(apiEndpoint, expiredConfirmationToken.toString())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.type").isEqualTo("http://notification-service/booking-confirmation-expired");

  }

  @Test
  @DisplayName("should return a OK if token is not yet expired")
  void confirmBooking_non_expired(){

    initializeDatabase()
    .block();

     webClient.get()
        .uri(apiEndpoint, goodConfirmationToken.toString())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
  }

  //endregion ConfirmEvent Test Scenarios

  //region Helper Methods

  /// Initialize database for each run
  Mono<Void> initializeDatabase() {
    var confirmations = List.of(
        BookingConfirmation.builder()
            .bookingUser("test_user1")
            .bookingEmail("test_user1@test.com")
            .eventId(1)
            .bookingId(1)
            .confirmationString(goodConfirmationToken)
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(10))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .bookingUser("test_user2")
            .bookingEmail("test_user2@test.com")
            .eventId(1)
            .bookingId(1)
            .confirmationString(expiredConfirmationToken)
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(90))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build()
    );

    return bookingConfirmationRepository.deleteAll()
        .then(bookingConfirmationRepository.saveAll(confirmations).then());
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
        .withExposedService("postgres-notifications-test", 5432)
        .withExposedService("kafka-notifications-test", 9092);
  }

  //endregion Helper Methods

  //region TestConfig

  @TestConfiguration
  @ActiveProfiles({"test", "integration_test_containers"})
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

  //endregion TestConfig

}

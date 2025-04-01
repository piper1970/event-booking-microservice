package piper1970.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.topics.Topics;

@Tags({
    @Tag("integration-test"),
    @Tag("kafka-test")
})
@DisplayName("Event-Service: KafkaMessagePostingService IT")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"test", "integration_kafka"})
class KafkaMessagePostingServiceTestIT{

  //region Properties Used

  private final Duration timeoutDuration = Duration.ofSeconds(4);

  @Autowired
  private KafkaMessagePostingService kafkaMessagePostingService;

  @Autowired
  private ConsumerFactory<Integer, Object> consumerFactory;

  // mock consumer to test message-posting-service logic
  private Consumer<Integer, Object> testConsumer;

  //endregion Properties Used

  //region Before/After

  @BeforeEach
  void setUp() {
    testConsumer = consumerFactory.createConsumer();
    testConsumer.subscribe(List.of(Topics.EVENT_CANCELLED,
        Topics.EVENT_CHANGED, Topics.EVENT_COMPLETED));
  }

  @AfterEach
  void tearDown() {
    testConsumer.close();
  }

  //endregion Before/After

  //region Tests

  @Test
  void postEventCancelledMessage() {

    var message = new EventCancelled();
    message.setEventId(1);
    message.setMessage("Test event cancelled message");

    kafkaMessagePostingService.postEventCancelledMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.EVENT_CANCELLED,
        timeoutDuration);

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);
  }

  @Test
  void postEventChangedMessage() {

    var message = new EventChanged();
    message.setEventId(1);
    message.setMessage("Test event changed message");

    kafkaMessagePostingService.postEventChangedMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.EVENT_CHANGED,
        timeoutDuration);

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);
  }

  @Test
  void postEventCompletedMessage() {

    var message = new EventCompleted();
    message.setEventId(1);
    message.setMessage("Test event completed message");

    kafkaMessagePostingService.postEventCompletedMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.EVENT_COMPLETED,
        timeoutDuration);

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);
  }

  //endregion Tests

  //region Configuration

  @TestConfiguration
  @ActiveProfiles({"test", "integration_kafka"})
  static class TestConfig{
    @Bean
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
      String registryUrl = kafkaProperties.getProperties().get("schema.registry.url");
      var scope = MockSchemaRegistry.validateAndMaybeGetMockScope(List.of(registryUrl));
      return MockSchemaRegistry.getClientForScope(scope);
    }
  }

  //endregion Configuration

}
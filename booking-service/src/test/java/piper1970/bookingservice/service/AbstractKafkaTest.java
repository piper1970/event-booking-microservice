package piper1970.bookingservice.service;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.util.List;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import piper1970.eventservice.common.topics.Topics;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" },
topics = {
    Topics.BOOKING_CREATED, Topics.BOOKING_CANCELLED, Topics.BOOKINGS_UPDATED,
    Topics.BOOKINGS_CANCELLED, Topics.BOOKING_CONFIRMED, Topics.EVENT_CHANGED,
    Topics.EVENT_CANCELLED, Topics.BOOKING_EVENT_UNAVAILABLE, Topics.EVENT_COMPLETED
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"test", "integration_kafka"})
public abstract class AbstractKafkaTest {

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
}

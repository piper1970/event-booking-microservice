package piper1970.eventservice.common.testing;

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.Map;

public class TestingAvroSerializer extends KafkaAvroSerializer {

  public TestingAvroSerializer() {
    super();
    super.schemaRegistry = new MockSchemaRegistryClient();
  }

  public TestingAvroSerializer(SchemaRegistryClient schemaRegistryClient) {
    super(new MockSchemaRegistryClient());
  }

  public TestingAvroSerializer(SchemaRegistryClient schemaRegistryClient, Map<String, ?> props) {
    super(new MockSchemaRegistryClient(), props);
  }
}

package piper1970.eventservice.common.testing;

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.Schema;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;

public class TestingAvroDeserializer extends KafkaAvroDeserializer {

  // TODO: add breakpoints to see how deserialization is working

  @Override
  public Object deserialize(String topic, byte[] bytes) {
    switch (topic) {
      case Topics.BOOKING_CANCELLED -> this.schemaRegistry = getMockClient(BookingCancelled.SCHEMA$);
      case Topics.BOOKING_CONFIRMED -> this.schemaRegistry = getMockClient(BookingConfirmed.SCHEMA$);
      case Topics.BOOKING_CREATED -> this.schemaRegistry = getMockClient(BookingCreated.SCHEMA$);
      case Topics.BOOKING_EVENT_UNAVAILABLE -> this.schemaRegistry = getMockClient(
          BookingEventUnavailable.SCHEMA$);
      case Topics.BOOKINGS_CANCELLED -> this.schemaRegistry = getMockClient(BookingsCancelled.SCHEMA$);
      case Topics.BOOKINGS_UPDATED -> this.schemaRegistry = getMockClient(BookingsUpdated.SCHEMA$);
      case Topics.EVENT_CANCELLED -> this.schemaRegistry = getMockClient(EventCancelled.SCHEMA$);
      case Topics.EVENT_CHANGED -> this.schemaRegistry = getMockClient(EventChanged.SCHEMA$);
      default -> throw new IllegalStateException("Unexpected value: " + topic);
    }
    return super.deserialize(topic, bytes);
  }

  private static SchemaRegistryClient getMockClient(final Schema schema){
    return new MockSchemaRegistryClient() {
      public synchronized Schema getById(int id) {
        return schema;
      }
    };
  }
}

package piper1970.bookingservice.config;

import brave.Tracer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import piper1970.eventservice.common.kafka.TopicCreater;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.kafka.receiver.MicrometerConsumerListener;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.observation.KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.MicrometerProducerListener;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.observation.KafkaSenderObservation.DefaultKafkaSenderObservationConvention;

@Configuration(proxyBeanMethods = false)
@EnableKafka
public class KafkaConfig {

  @Value("${kafka.replication.factor}")
  private Integer replicationFactor;

  @Value("${kafka.partition.count}")
  private Integer partitionCount;

  private final String kafkaRetentionProperty;

  public KafkaConfig(@Value("${kafka.retention.days}") Integer retentionDays) {
    kafkaRetentionProperty = String.valueOf(Duration.ofDays(retentionDays).toMillis());
  }

  @Bean
  TopicCreater topicCreater() {
    return new TopicCreater(partitionCount, replicationFactor, kafkaRetentionProperty);
  }

  //region Topic Creation

  //region Producer Topics

  @Bean
  NewTopic createBookingCreatedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CREATED);
  }

  @Bean
  NewTopic createBookingCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CANCELLED);
  }

  @Bean
  NewTopic createBookingsUpdatedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKINGS_UPDATED);
  }

  @Bean
  NewTopic createBookingsCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKINGS_CANCELLED);
  }

  //endregion Producer Topics

  //region Consumer Topics

  @Bean
  NewTopic createBookingConfirmedTopic(TopicCreater topicCreater){
    return topicCreater.create(Topics.BOOKING_CONFIRMED);
  }

  @Bean
  NewTopic createBookingExpiredTopic(TopicCreater topicCreater){
    return topicCreater.create(Topics.BOOKING_EXPIRED);
  }

  @Bean
  NewTopic eventChangedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.EVENT_CHANGED);
  }

  @Bean
  NewTopic eventCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.EVENT_CANCELLED);
  }

  @Bean
  NewTopic bookingEventUnavailableTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_EVENT_UNAVAILABLE);
  }

  @Bean
  NewTopic eventCompletedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.EVENT_COMPLETED);
  }

  //endregion Consumer Topics

  //endregion Topic Creation

  //region Kafka Producer

  @Bean
  KafkaSender<Integer, Object> kafkaSender(KafkaProperties kafkaProperties, ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    var senderOptions = SenderOptions.<Integer, Object>create(propertiesMap)
        .withObservation(observationRegistry,
            new DefaultKafkaSenderObservationConvention()) // needed for capturing correlation-id in spanned logs
        .producerListener(new MicrometerProducerListener(meterRegistry));
    return KafkaSender.create(senderOptions);
  }

  @Bean
  DeadLetterTopicProducer deadLetterTopicProducer(KafkaSender<Integer, Object> kafkaSender,
      Tracer tracer, @Value("${kafka.dlt.suffix:-bs-dlt}") String deadLetterTopicSuffix,
      Clock clock) {
    return new DeadLetterTopicProducer(kafkaSender, tracer, deadLetterTopicSuffix, clock);
  }

  //endregion Kafka Producer

  //region Kafka Consumer

  @Bean
  public ReceiverOptions<Integer, Object> receiverOptions(KafkaProperties kafkaProperties, ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    return ReceiverOptions.<Integer, Object>create(kafkaProperties.buildConsumerProperties())
        .withObservation(observationRegistry,
            new DefaultKafkaReceiverObservationConvention()) // needed for capturing correlation-id in spanned logs
        .consumerListener(new MicrometerConsumerListener(meterRegistry));
  }

  @Bean
  public ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory(ReceiverOptions<Integer, Object> receiverOptions) {
    var topics = List.of(Topics.BOOKING_CONFIRMED, Topics.BOOKING_EXPIRED,
        Topics.EVENT_CHANGED, Topics.EVENT_CANCELLED, Topics.BOOKING_EVENT_UNAVAILABLE, Topics.EVENT_COMPLETED);
    return new ReactiveKafkaReceiverFactory(receiverOptions, topics);
  }

  //endregion Kafka Consumer

}

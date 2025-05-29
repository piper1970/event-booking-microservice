package piper1970.eventservice.config;

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
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.MicrometerProducerListener;
import reactor.kafka.sender.SenderOptions;

@Configuration
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
  NewTopic createBookingEventUnavailableTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_EVENT_UNAVAILABLE);
  }

  @Bean
  NewTopic createEventCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.EVENT_CANCELLED);
  }

  @Bean
  NewTopic createEventChangedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.EVENT_CHANGED);
  }

  @Bean
  NewTopic createEventCompletedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.EVENT_COMPLETED);
  }

  //endregion Producer Topics

  //region Consumer Topics

  @Bean
  NewTopic createBookingCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CANCELLED);
  }

  @Bean
  NewTopic createBookingConfirmedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CONFIRMED);
  }

  //endregion Consumer Topics

  //endregion Topic Creation

  //region Kafka Producer

  @Bean
  KafkaSender<Integer, Object> kafkaSender(KafkaProperties kafkaProperties, ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    var senderOptions = SenderOptions.<Integer, Object>create(propertiesMap)
        .producerListener(new MicrometerProducerListener(meterRegistry))
        .withObservation(observationRegistry);
    return KafkaSender.create(senderOptions);
  }

  @Bean
  DeadLetterTopicProducer deadLetterTopicProducer(KafkaSender<Integer, Object> kafkaSender,
      @Value("${kafka.dlt.suffix:-es-dlt}") String deadLetterTopicSuffix, Clock clock) {
    return new DeadLetterTopicProducer(kafkaSender, deadLetterTopicSuffix, clock);
  }

  //endregion Kafka Producer

  //region Kafka Consumer

  @Bean
  public ReceiverOptions<Integer, Object> receiverOptions(KafkaProperties kafkaProperties, ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    return ReceiverOptions.<Integer, Object>create(kafkaProperties.buildConsumerProperties())
        .consumerListener(new MicrometerConsumerListener(meterRegistry))
        .withObservation(observationRegistry);
  }

  @Bean
  public ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory(ReceiverOptions<Integer, Object> receiverOptions) {
    var topics = List.of(Topics.BOOKING_CONFIRMED, Topics.BOOKING_CANCELLED);
    return new ReactiveKafkaReceiverFactory(receiverOptions, topics);
  }

  //endregion Kafka Consumer

}

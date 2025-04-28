package piper1970.bookingservice.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import piper1970.eventservice.common.kafka.TopicCreater;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;

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
  ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate(KafkaProperties kafkaProperties) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    return new ReactiveKafkaProducerTemplate<>(SenderOptions.create(propertiesMap));
  }

  @Bean
  DeadLetterTopicProducer deadLetterTopicProducer(ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate,
      @Value("${kafka.dlt.suffix : -bs-dlt}") String deadLetterTopicSuffix) {
    return new DeadLetterTopicProducer(reactiveKafkaProducerTemplate, deadLetterTopicSuffix);
  }

  //endregion Kafka Producer

  //region Kafka Consumer

  @Bean
  public ReceiverOptions<Integer, Object> receiverOptions(KafkaProperties kafkaProperties) {
    return ReceiverOptions.create(kafkaProperties.buildConsumerProperties());
  }

  @Bean
  public ReactiveKafkaReceiverFactory reactiveKafkaConsumerFactory(ReceiverOptions<Integer, Object> receiverOptions) {
    var topics = List.of(Topics.BOOKING_CONFIRMED, Topics.BOOKING_EXPIRED,
        Topics.EVENT_CHANGED, Topics.EVENT_CANCELLED, Topics.BOOKING_EVENT_UNAVAILABLE, Topics.EVENT_COMPLETED);
    return new ReactiveKafkaReceiverFactory(receiverOptions, topics);
  }

  //endregion Kafka Consumer

}

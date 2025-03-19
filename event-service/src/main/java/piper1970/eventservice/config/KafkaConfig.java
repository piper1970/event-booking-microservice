package piper1970.eventservice.config;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import piper1970.eventservice.common.kafka.TopicCreater;
import piper1970.eventservice.common.topics.Topics;

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

  //region Kafka Template Creation

  @Bean
  ProducerFactory<Integer, Object> producerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    return new DefaultKafkaProducerFactory<>(propertiesMap);
  }

  @Bean
  KafkaTemplate<Integer, Object> kafkaTemplate(ProducerFactory<Integer, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  //endregion Kafka Template Creation

}

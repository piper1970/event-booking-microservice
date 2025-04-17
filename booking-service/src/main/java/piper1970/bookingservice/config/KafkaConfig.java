package piper1970.bookingservice.config;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import piper1970.eventservice.common.kafka.EventBookingListenerErrorHandler;
import piper1970.eventservice.common.kafka.TopicCreater;
import piper1970.eventservice.common.topics.Topics;

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
  ProducerFactory<Integer,Object> producerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    return new DefaultKafkaProducerFactory<>(propertiesMap);
  }

  @Bean
  KafkaTemplate<Integer, Object> kafkaTemplate(ProducerFactory<Integer, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  //endregion Kafka Producer

  //region Kafka Consumer

  @Bean
  public ConsumerFactory<Integer, Object> consumerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> propertiesMap = kafkaProperties.buildConsumerProperties();
    return new DefaultKafkaConsumerFactory<>(propertiesMap);
  }

  @Bean
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<Integer, Object>> kafkaListenerContainerFactory(
      ConsumerFactory<Integer, Object> consumerFactory
  ){
    ConcurrentKafkaListenerContainerFactory<Integer, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setConcurrency(partitionCount * 5); // 5 consumer topics X # of partitions
    return factory;
  }

  @Bean
  public KafkaListenerErrorHandler kafkaListenerErrorHandler(
      DeadLetterPublishingRecoverer deadLetterPublishingRecoverer
  ) {
    return new EventBookingListenerErrorHandler(deadLetterPublishingRecoverer);
  }

  @Bean
  public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Integer, Object> kafkaTemplate) {
    BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> retryFunction = (cr, e) -> new TopicPartition(
        cr.topic() + "-bs-dlt", cr.partition()
    );
    var dlt = new DeadLetterPublishingRecoverer(kafkaTemplate, retryFunction);
    dlt.setLogRecoveryRecord(true);
    return dlt;
  }

  //endregion Kafka Consumer

}

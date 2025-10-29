package piper1970.eventservice.common.kafka;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

/**
 * Kafka helper class for common reactive kafka functions
 */
public class KafkaHelper {

  /**
   * Reactive onNext Consumer builder, creating consumers that log the kafka messages.
   *
   * @param service microservice in use
   * @param log logger to log messages to
   * @return Consumer[SenderResult[Long]] for logging reactive kafka messages in the pipeline.
   */
  public static Consumer<SenderResult<Long>> postReactiveOnNextConsumer(String service, Logger log){
    return (senderResult) -> {
      var metadata = senderResult.recordMetadata();
      var topic = metadata.topic();
      var offset = metadata.offset();
      var timestamp = metadata.timestamp();
      log.debug("message sent to topic [{}] from [{}] at timestamp [{}] to offset {}", topic, service, timestamp, offset);
    };
  }

  /**
   * Wrapper function to send messages through a KafkaSender, appending a time-based correlationId to each message.
   *
   * @param topic Topic to send message to
   * @param key Integer key value
   * @param value Message to send
   * @param clock Clock instance to create correlationMetaDataId based off current instance in epoch milliseconds
   * @param headers Headers to be added to record.
   * @return SenderRecord Mono
   */
  public static Mono<SenderRecord<Integer, Object, Long>> createSenderMono(String topic, Integer key, Object value, Clock clock, Iterable<Header> headers) {
    var  correlationId = Instant.now(clock).toEpochMilli();
    return Mono.just(SenderRecord.create(new ProducerRecord<>(topic, null, key, value, headers), correlationId));
  }
}

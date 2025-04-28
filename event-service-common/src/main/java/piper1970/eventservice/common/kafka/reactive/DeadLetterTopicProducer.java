package piper1970.eventservice.common.kafka.reactive;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

@Slf4j
@RequiredArgsConstructor
public class DeadLetterTopicProducer {

  private final ReactiveKafkaProducerTemplate<Integer, Object> template;
  private final String topicSuffix;

  public Mono<SenderResult<Long>> process(ReceiverRecord<Integer, Object> record) {
    var senderRecord = toSenderRecord(record);
    return template.send(senderRecord)
        .doOnNext(result -> log.info("DLT message sent to topic [{}] with correlationData [{}]", result.recordMetadata().topic(), result.correlationMetadata()))
        .doOnError(err -> log.error(" Error publishing DLT message to topic [{}]", senderRecord.topic(), err));
  }


  private SenderRecord<Integer, Object, Long> toSenderRecord(
      ReceiverRecord<Integer, Object> record) {
    var dltTopic = record.topic() + topicSuffix;
    var correlationMetaData = Instant.now().getEpochSecond();
    var producerRecord = new ProducerRecord<>(dltTopic, record.key(), record.value());
    return SenderRecord.create(producerRecord, correlationMetaData);
  }

}

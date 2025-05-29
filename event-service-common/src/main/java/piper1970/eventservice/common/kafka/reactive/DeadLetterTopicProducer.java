package piper1970.eventservice.common.kafka.reactive;

import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;

@Slf4j
@RequiredArgsConstructor
public class DeadLetterTopicProducer {

  private final KafkaSender<Integer, Object> kafkaSender;
  private final String topicSuffix;
  private final Clock clock;

  public Mono<SenderResult<Long>> process(ReceiverRecord<Integer, Object> record) {
    var dltTopic = record.topic() + topicSuffix;
    return kafkaSender.send(createSenderMono(dltTopic, record.key(), record.value(), clock))
        .subscribeOn(Schedulers.boundedElastic())
        .single()
        .doOnNext(result -> log.info("DLT message sent to topic [{}] with correlationData [{}]", result.recordMetadata().topic(), result.correlationMetadata()))
        .doOnError(err -> log.error(" Error publishing DLT message to topic [{}]", dltTopic, err));
  }

}

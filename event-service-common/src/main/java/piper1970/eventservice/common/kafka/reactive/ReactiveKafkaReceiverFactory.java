package piper1970.eventservice.common.kafka.reactive;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverPartition;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Factory for accessing individual kafka receivers
 */
public class ReactiveKafkaReceiverFactory {

  /**
   * topic-key-based receiver map
   */
  private final Map<String, KafkaReceiver<Integer, Object>> kafkaReceiverMap;

  public ReactiveKafkaReceiverFactory(@NonNull ReceiverOptions<Integer, Object> receiverOptions,
      @NonNull List<String> topics) {

    kafkaReceiverMap = topics.stream()
        .map(topic -> {
          var receiver = createReceiver(receiverOptions, topic);
          return Tuples.of(topic, receiver);
        })
        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
  }

  private KafkaReceiver<Integer, Object> createReceiver(ReceiverOptions<Integer, Object> receiverOptions,
      String topic) {
    receiverOptions = receiverOptions
        .subscription(List.of(topic))
        // seek latest...
        .addAssignListener(partitions -> partitions.forEach(ReceiverPartition::seekToEnd));
    return KafkaReceiver.create(receiverOptions);
  }

  /**
   * Access given kafka receiver based on key/topic string provided
   *
   * @param topic accessor for receiver
   * @return KafkaReceiver[Int, Object]
   * @throws IllegalArgumentException if topic is unrecognized
   */
  public KafkaReceiver<Integer, Object> getReceiver(@NonNull String topic){
    return Optional.ofNullable(kafkaReceiverMap.get(topic))
        .orElseThrow(() -> new IllegalArgumentException("Unknown topic: " + topic));
  }
}

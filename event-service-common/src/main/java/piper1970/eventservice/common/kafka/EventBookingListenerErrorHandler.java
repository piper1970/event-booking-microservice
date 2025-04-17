package piper1970.eventservice.common.kafka;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;

/**
 * KafkaListenerErrorHandler implementation that sends message to dead letter topic if it exceeds
 * the given retryLimit.
 */
@RequiredArgsConstructor
@Slf4j
public class EventBookingListenerErrorHandler implements KafkaListenerErrorHandler {

  private final DeadLetterPublishingRecoverer deadLetterPublishingRecoverer;

  @Override
  @NonNull
  public Object handleError(Message<?> msg, @NonNull ListenerExecutionFailedException ex) {
    // capture info needed for ConsumerRecord
    var topic = Objects.requireNonNull(
        msg.getHeaders().get(KafkaHeaders.RECEIVED_TOPIC, String.class));
    var partition = Objects.requireNonNull(
        msg.getHeaders().get(KafkaHeaders.RECEIVED_PARTITION, Integer.class));
    var offset = Objects.requireNonNull(msg.getHeaders().get(KafkaHeaders.OFFSET, Long.class));
    var key = msg.getHeaders().get(KafkaHeaders.RECEIVED_KEY, Integer.class);
    var value = msg.getPayload();

    log.error(
        "Failed to handle from topic [{}] partition [{}] offset [{}] key [{}]. Sending to DLT topic",
        topic, partition, offset, key);

    var consumerRecord = new ConsumerRecord<>(topic, partition, offset, key, value);

    // send to Dead Letter Topic.  topic() + ".dlt"
    deadLetterPublishingRecoverer.accept(consumerRecord, ex);
    return "Failed...sent to DLT"; // value ignored by listener
  }
}

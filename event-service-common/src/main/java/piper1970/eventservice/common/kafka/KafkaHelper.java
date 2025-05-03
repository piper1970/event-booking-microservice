package piper1970.eventservice.common.kafka;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.springframework.kafka.support.SendResult;
import reactor.kafka.sender.SenderResult;
import reactor.util.retry.Retry;

public class KafkaHelper {

  public static  final Retry DEFAULT_RETRY = Retry.backoff(3L, Duration.ofMillis(500L))
      .jitter(0.7D);

  public static BiConsumer<SendResult<Integer, Object>, Throwable> postResponseConsumer(String service, Logger log){
    return (sendResult, throwable) -> {
      if(sendResult != null) {
        var metaData = sendResult.getRecordMetadata();
        var topic = metaData.topic();
        var offset = metaData.offset();
        var timestamp = metaData.timestamp();
        log.debug("message sent to topic [{}] from [{}] at timestamp [{}] to offset {}", topic, service, timestamp, offset);
      }
      else{
        log.error(throwable.getMessage(), throwable);
      }
    };
  }

  public static Consumer<SenderResult<Void>> postReactiveOnNextConsumer(String service, Logger log){
    return (senderResult) -> {
      var metadata = senderResult.recordMetadata();
      var topic = metadata.topic();
      var offset = metadata.offset();
      var timestamp = metadata.timestamp();
      log.debug("message sent to topic [{}] from [{}] at timestamp [{}] to offset {}", topic, service, timestamp, offset);
    };
  }
}

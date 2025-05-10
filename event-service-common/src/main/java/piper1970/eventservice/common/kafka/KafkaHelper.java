package piper1970.eventservice.common.kafka;

import java.util.function.Consumer;
import org.slf4j.Logger;
import reactor.kafka.sender.SenderResult;

public class KafkaHelper {

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

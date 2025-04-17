package piper1970.eventservice.common.kafka;

import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.springframework.kafka.support.SendResult;

public class KafkaHelper {

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
}

package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.topics.Topics;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessagePostingService implements MessagePostingService {

  private final KafkaTemplate<Integer, Object> kafkaTemplate;
  private static final String SERVICE_NAME = "event-service";

  @Override
  public void postEventCancelledMessage(EventCancelled message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting EVENT_CANCELLED message [{}]", eventId);
      kafkaTemplate.send(Topics.EVENT_CANCELLED, eventId, message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventCancelled message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postEventChangedMessage(EventChanged message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting EVENT_CHANGED message [{}]", eventId);
      kafkaTemplate.send(Topics.EVENT_CHANGED, eventId, message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventChanged message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postEventCompletedMessage(EventCompleted message) {
    // TODO: need to figure out how to trigger this logic.
    //  Possibly a Scheduled process, with a configurable timerange
    try{
      var eventId = message.getEventId();
      log.debug("Posting EVENT_COMPLETED message [{}]", eventId);
      kafkaTemplate.send(Topics.EVENT_COMPLETED, eventId, message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventCompleted message to kafka: {}", e.getMessage(), e);
    }
  }
}

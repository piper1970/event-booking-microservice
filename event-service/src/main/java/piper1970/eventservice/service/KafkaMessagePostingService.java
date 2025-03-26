package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.topics.Topics;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessagePostingService implements MessagePostingService {

  private final KafkaTemplate<Integer, Object> kafkaTemplate;

  @Override
  public void postBookingEventUnavailableMessage(BookingEventUnavailable message) {
    try{
      kafkaTemplate.send(Topics.BOOKING_EVENT_UNAVAILABLE, message.getEventId(), message)
          .whenComplete(this::logPostResponse);
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingEventUnavailable message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postEventCancelledMessage(EventCancelled message) {
    try{
      kafkaTemplate.send(Topics.EVENT_CANCELLED, message.getEventId(), message)
          .whenComplete(this::logPostResponse);
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventCancelled message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postEventChangedMessage(EventChanged message) {
    try{
      kafkaTemplate.send(Topics.EVENT_CHANGED, message.getEventId(), message)
          .whenComplete(this::logPostResponse);
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventChanged message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postEventCompletedMessage(EventCompleted message) {
    try{
      kafkaTemplate.send(Topics.EVENT_COMPLETED, message.getEventId(), message)
          .whenComplete(this::logPostResponse);
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventCompleted message to kafka: {}", e.getMessage(), e);
    }
  }

  private void logPostResponse(SendResult<Integer, Object> sendResult, Throwable throwable) {
    if(sendResult != null) {
      var metaData = sendResult.getRecordMetadata();
      var topic = metaData.topic();
      var offset = metaData.offset();
      var timestamp = metaData.timestamp();
      log.debug("message sent to topic [{}] from event-service at timestamp [{}] to offset {}", topic, timestamp, offset);
    }else{
      log.error(throwable.getMessage(), throwable);
    }
  }
}

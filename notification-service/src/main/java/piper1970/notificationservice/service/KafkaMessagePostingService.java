package piper1970.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessagePostingService implements MessagePostingService {

  private final KafkaTemplate<Integer, Object> kafkaTemplate;

  @Override
  public void postBookingConfirmedMessage(BookingConfirmed message) {
    try{
      kafkaTemplate.send(Topics.BOOKING_CONFIRMED, message.getEventId(), message)
          .whenComplete(this::logPostResponse);
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingConfirmed message to kafka: {}", e.getMessage(), e);
    }
  }

  private void logPostResponse(SendResult<Integer, Object> sendResult, Throwable throwable) {
    if (sendResult != null) {
      var metaData = sendResult.getRecordMetadata();
      var topic = metaData.topic();
      var offset = metaData.offset();
      var timestamp = metaData.timestamp();
      log.debug("message sent to topic [{}] from notification-service at timestamp [{}] to offset {}", topic, timestamp, offset);
    }else{
      log.error(throwable.getMessage(), throwable);
    }

  }
}

package piper1970.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.eventservice.common.topics.Topics;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessagePostingService implements MessagePostingService {

  private final KafkaTemplate<Integer, Object> kafkaTemplate;
  private static final String SERVICE_NAME = "notification-service";

  @Override
  public void postBookingConfirmedMessage(BookingConfirmed message) {
    try{
      kafkaTemplate.send(Topics.BOOKING_CONFIRMED, message.getEventId(), message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingConfirmed message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postBookingExpiredMessage(BookingExpired message) {
    try{
      kafkaTemplate.send(Topics.BOOKING_EXPIRED, message.getEventId(), message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingExpired message to kafka: {}", e.getMessage(), e);
    }
  }
}

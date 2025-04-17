package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.topics.Topics;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessagePostingService implements MessagePostingService {

  private final KafkaTemplate<Integer, Object> kafkaTemplate;

  private static final String SERVICE_NAME = "booking-service";

  @Override
  public void postBookingCreatedMessage(BookingCreated message) {
    try{
      kafkaTemplate.send(Topics.BOOKING_CREATED, message.getEventId(), message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingCreated message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postBookingCancelledMessage(BookingCancelled message) {
    try{
      kafkaTemplate.send(Topics.BOOKING_CANCELLED, message.getEventId(), message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingCancelled message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postBookingsUpdatedMessage(BookingsUpdated message) {
    try{
      kafkaTemplate.send(Topics.BOOKINGS_UPDATED, message.getEventId(), message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingsUpdated message to kafka: {}", e.getMessage(), e);
    }
  }

  @Override
  public void postBookingsCancelledMessage(BookingsCancelled message) {
    try{
      kafkaTemplate.send(Topics.BOOKINGS_CANCELLED, message.getEventId(), message)
          .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingsCancelled message to kafka: {}", e.getMessage(), e);
    }
  }
}

package piper1970.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReactiveKafkaMessagePostingService implements MessagePostingService {

  private final ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate;
  private static final String SERVICE_NAME = "notification-service";

  @Override
  public Mono<Void> postBookingConfirmedMessage(BookingConfirmed message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting BOOKING_CONFIRMED message [{}]", eventId);
      return reactiveKafkaProducerTemplate.send(Topics.BOOKING_CONFIRMED, eventId, message)
          .subscribeOn(Schedulers.boundedElastic())
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
          .doOnError(throwable -> log.error("Error sending BOOKING_CONFIRMED message: {}", throwable.getMessage(), throwable))
          .then();
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingConfirmed message to kafka: {}", e.getMessage(), e);
      return Mono.error(e);
    }
  }

  @Override
  public Mono<Void> postBookingExpiredMessage(BookingExpired message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting BOOKING_EXPIRED message [{}]", eventId);
      return reactiveKafkaProducerTemplate.send(Topics.BOOKING_EXPIRED, eventId, message)
          .subscribeOn(Schedulers.boundedElastic())
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
          .doOnError(throwable -> log.error("Error sending BOOKING_EXPIRED message: {}", throwable.getMessage(), throwable))
          .then();
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingExpired message to kafka: {}", e.getMessage(), e);
      return Mono.error(e);
    }
  }
}

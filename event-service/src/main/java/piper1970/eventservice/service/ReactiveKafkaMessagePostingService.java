package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveKafkaMessagePostingService implements MessagePostingService{

  private final ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate;
  private static final String SERVICE_NAME = "event-service";

  @Override
  public Mono<Void> postEventCancelledMessage(EventCancelled message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting EVENT_CANCELLED message [{}]", eventId);
      return reactiveKafkaProducerTemplate.send(Topics.EVENT_CANCELLED, eventId, message)
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
          .doOnError(throwable -> log.error("Error sending EVENT_CANCELLED message: {}", throwable.getMessage(), throwable))
          .then();
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventCancelled message to kafka: {}", e.getMessage(), e);
      return Mono.error(e);
    }
  }

  @Override
  public Mono<Void> postEventChangedMessage(EventChanged message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting EVENT_CHANGED message [{}]", eventId);
      return reactiveKafkaProducerTemplate.send(Topics.EVENT_CHANGED, eventId, message)
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
          .doOnError(throwable -> log.error("Error sending EVENT_CHANGED message: {}", throwable.getMessage(), throwable))
          .then();
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventChanged message to kafka: {}", e.getMessage(), e);
      return Mono.error(e);
    }
  }

  @Override
  public Mono<Void> postEventCompletedMessage(EventCompleted message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting EVENT_COMPLETED message [{}]", eventId);
      return reactiveKafkaProducerTemplate.send(Topics.EVENT_COMPLETED, eventId, message)
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
          .doOnError(throwable -> log.error("Error sending EVENT_COMPLETED message: {}", throwable.getMessage(), throwable))
          .then();
    }catch(Exception e){
      log.error("Unknown error occurred while posting EventCompleted message to kafka: {}", e.getMessage(), e);
      return Mono.error(e);
    }
  }
}

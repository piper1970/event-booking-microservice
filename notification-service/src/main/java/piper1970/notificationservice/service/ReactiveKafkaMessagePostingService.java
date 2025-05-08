package piper1970.notificationservice.service;

import static piper1970.eventservice.common.kafka.KafkaHelper.DEFAULT_RETRY;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.exceptions.KafkaPostingException;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.SenderResult;

@Service
@Slf4j
public class ReactiveKafkaMessagePostingService implements MessagePostingService {

  private static final String SERVICE_NAME = "notification-service";

  private final ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate;
  private final Duration postingTimeout;

  public ReactiveKafkaMessagePostingService(
      ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate,
      @Value("${kafka.posting.timout.milliseconds:1500}") Long postingTimeoutMillis
  ){
    this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
    this.postingTimeout = Duration.ofMillis(postingTimeoutMillis);
  }

  @Override
  public Mono<Void> postBookingConfirmedMessage(BookingConfirmed message) {
    try{
      var eventId = message.getEventId();
      log.debug("Posting BOOKING_CONFIRMED message [{}]", eventId);
      return reactiveKafkaProducerTemplate.send(Topics.BOOKING_CONFIRMED, eventId, message)
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .timeout(postingTimeout)
          .retryWhen(DEFAULT_RETRY)
          .onErrorResume(ex -> handlePostingTimeout(ex, eventId, "BOOKING_CONFIRMED"))
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
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
          .log()
          .timeout(postingTimeout)
          .retryWhen(DEFAULT_RETRY)
          .onErrorResume(ex -> handlePostingTimeout(ex, eventId, "BOOKING_EXPIRED"))
          .doOnSuccess(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
          .then();
    }catch(Exception e){
      log.error("Unknown error occurred while posting BookingExpired message to kafka: {}", e.getMessage(), e);
      return Mono.error(e);
    }
  }

  private Mono<SenderResult<Void>> handlePostingTimeout(Throwable ex, Integer bookId, String subMessage) {
    if (Exceptions.isRetryExhausted(ex)) {
      return Mono.error(new KafkaPostingException(
          providePostingTimeoutErrorMessage(
              "attempting to post %s message with key [%d]. Exhausted all retries".formatted(
                  subMessage, bookId)), ex.getCause()));
    }
    return Mono.error(new KafkaPostingException(
        providePostingTimeoutErrorMessage(
            "attempting to post %s message with key [%d]".formatted(subMessage, bookId)), ex));
  }

  private String providePostingTimeoutErrorMessage(String subMessage) {
    return String.format("Message posting for booking timed out [over %d milliseconds] %s",
        postingTimeout.toMillis(), subMessage);
  }
}

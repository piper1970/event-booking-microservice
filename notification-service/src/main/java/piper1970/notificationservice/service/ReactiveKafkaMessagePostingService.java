package piper1970.notificationservice.service;

import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;
import static piper1970.eventservice.common.kafka.reactive.TracingHelper.extractMDCIntoHeaders;

import brave.Tracer;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.exceptions.KafkaPostingException;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;
import reactor.util.retry.Retry;

/**
 * Service for posting confirmation and expired messages to Kafka topics
 *
 * @see BookingConfirmed
 * @see BookingExpired
*/
@Service
@Slf4j
public class ReactiveKafkaMessagePostingService implements MessagePostingService {

  private static final String SERVICE_NAME = "notification-service";

  private final KafkaSender<Integer, Object> kafkaSender;
  private final Tracer tracer;
  private final Duration postingTimeout;
  private final Retry defaultKafkaRetry;
  private final Clock clock;

  public ReactiveKafkaMessagePostingService(
      KafkaSender<Integer, Object> kafkaSender,
      Tracer tracer,
      @Value("${kafka.posting.timout.milliseconds:1500}") Long postingTimeoutMillis,
      @Qualifier("kafka") Retry defaultKafkaRetry,
      Clock clock
  ) {
    this.kafkaSender = kafkaSender;
    this.tracer = tracer;
    this.postingTimeout = Duration.ofMillis(postingTimeoutMillis);
    this.defaultKafkaRetry = defaultKafkaRetry;
    this.clock = clock;
  }

  /**
   * Handles posting {@link BookingConfirmed } messages to Kafka.
   *
   * @param message  message to post to Kafka booking-confirmed topic
   * @return Mono[Void]
   */
  @Override
  public Mono<Void> postBookingConfirmedMessage(BookingConfirmed message) {
    return Mono.deferContextual(context -> {
      try {
        var eventId = message.getEventId();
        log.info("Posting BOOKING_CONFIRMED message [{}]", eventId);
        return kafkaSender.send(
                createSenderMono(Topics.BOOKING_CONFIRMED, eventId, message, clock,
                    extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .timeout(postingTimeout)
            .retryWhen(defaultKafkaRetry)
            .onErrorResume(ex -> handlePostingTimeout(ex, eventId, "BOOKING_CONFIRMED"))
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting BookingConfirmed message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

  /**
   * Handles posting {@link BookingExpired } messages to Kafka.
   *
   * @param message  message to post to Kafka booking-expired topic
   * @return Mono[Void]
   */
  @Override
  public Mono<Void> postBookingExpiredMessage(BookingExpired message) {
    return Mono.deferContextual(context -> {
      try {
        var eventId = message.getEventId();
        log.debug("Posting BOOKING_EXPIRED message [{}]", eventId);
        return kafkaSender.send(
                createSenderMono(Topics.BOOKING_EXPIRED, eventId, message, clock,
                    extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .timeout(postingTimeout)
            .retryWhen(defaultKafkaRetry)
            .onErrorResume(ex -> handlePostingTimeout(ex, eventId, "BOOKING_EXPIRED"))
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting BookingExpired message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

  /**
   * Helper function for dealing with timeouts when attempting to post messages.
   */
  private Mono<SenderResult<Long>> handlePostingTimeout(Throwable ex, Integer bookId,
      String subMessage) {
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

  /**
   * Helper template to create timeout messages based off argument.
   */
  private String providePostingTimeoutErrorMessage(String subMessage) {
    return String.format("Message posting for booking timed out [over %d milliseconds] %s",
        postingTimeout.toMillis(), subMessage);
  }
}

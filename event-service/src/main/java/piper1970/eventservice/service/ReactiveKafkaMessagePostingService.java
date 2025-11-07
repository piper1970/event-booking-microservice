package piper1970.eventservice.service;

import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;
import static piper1970.eventservice.common.kafka.reactive.TracingHelper.extractMDCIntoHeaders;

import brave.Tracer;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;

/**
 * Service for posting kafka messages reactively to given topics.
 * <p>
 * The following messages are posted by this service:
 * <ul>
 *   <li>EventCancelled message => event-cancelled topic</li>
 *   <li>EventChanged message => event-changed topic</li>
 *   <li>EventCompleted message => event-completed topic</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveKafkaMessagePostingService implements MessagePostingService {

  private final KafkaSender<Integer, Object> kafkaSender;
  private final Tracer tracer;
  private final Clock clock;
  private static final String SERVICE_NAME = "event-service";

  // TODO: Mono.deferContextual even working? context not being used inside try block
  //   This was added because zipkin tracing was not capturing the traceId/spanId values
  //   from kafka posts. Currently, traceId is captured, but spanId isn't.
  //   does `spring.reactor.context-propagation=auto` property invalidate the need for this?

  @Override
  public Mono<Void> postEventCancelledMessage(EventCancelled message) {
    return Mono.deferContextual(context -> {
      try {
        var eventId = message.getEventId();
        log.info("Posting EVENT_CANCELLED message [{}]", eventId);
        return kafkaSender.send(
                createSenderMono(Topics.EVENT_CANCELLED, eventId, message, clock, extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .doOnError(throwable -> log.error("Error sending EVENT_CANCELLED message: {}",
                throwable.getMessage(), throwable))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting EventCancelled message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });

  }

  @Override
  public Mono<Void> postEventChangedMessage(EventChanged message) {
    return Mono.deferContextual(context -> {
      try {
        var eventId = message.getEventId();
        log.debug("Posting EVENT_CHANGED message [{}]", eventId);
        return kafkaSender.send(
                createSenderMono(Topics.EVENT_CHANGED, eventId, message, clock, extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .doOnError(throwable -> log.error("Error sending EVENT_CHANGED message: {}",
                throwable.getMessage(), throwable))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting EventChanged message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

  @Override
  public Mono<Void> postEventCompletedMessage(EventCompleted message) {
    return Mono.deferContextual(context -> {
      try {
        var eventId = message.getEventId();
        log.debug("Posting EVENT_COMPLETED message [{}]", eventId);
        return kafkaSender.send(
                createSenderMono(Topics.EVENT_COMPLETED, eventId, message, clock, extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .doOnError(throwable -> log.error("Error sending EVENT_COMPLETED message: {}",
                throwable.getMessage(), throwable))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting EventCompleted message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }
}



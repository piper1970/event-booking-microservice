package piper1970.eventservice.kafka.listeners;

import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;
import static piper1970.eventservice.common.kafka.reactive.TracingHelper.extractMDCIntoHeaders;

import brave.Tracer;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingConfirmedListener extends DiscoverableListener {

  private final EventRepository eventRepository;
  private final KafkaSender<Integer, Object> kafkaSender;
  private final Tracer tracer;
  private final Duration timeoutDuration;
  private final Retry defaultRepositoryRetry;
  private final Retry defaultKafkaRetry;
  private final Clock clock;
  private Disposable subscription;

  public BookingConfirmedListener(ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      EventRepository eventRepository,
      KafkaSender<Integer, Object> kafkaSender,
      Tracer tracer,
      DeadLetterTopicProducer deadLetterTopicProducer,
      @NonNull @Value("${event-repository.timout.milliseconds}") Integer timeoutInMilliseconds,
      @Qualifier("repository") Retry defaultRepositoryRetry,
      @Qualifier("kafka") Retry defaultKafkaRetry,
      Clock clock) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.eventRepository = eventRepository;
    this.kafkaSender = kafkaSender;
    this.tracer = tracer;
    timeoutDuration = Duration.ofMillis(timeoutInMilliseconds);
    this.defaultRepositoryRetry = defaultRepositoryRetry;
    this.defaultKafkaRetry = defaultKafkaRetry;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Override
  public void initializeReceiverFlux() {
    subscription = buildFluxRequest()
        .subscribe(rcv -> rcv.receiverOffset().acknowledge());
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_CONFIRMED;
  }

  /**
   * Helper method to handle confirmation requests per single record. In the event of a confirmation
   * to an event with no more available bookings, a BOOKING_EVENT_UNAVAILABLE message is sent to the
   * corresponding topic. Retries up to 3 times, returning empty mono if not successful.
   *
   * @param record ReceiverRecord containing BookingConfirmed message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingConfirmedListener::handleIndividualRequest started");

    if (record.value() instanceof BookingConfirmed message) {
      var eventId = message.getEventId();

      log.info("Consuming from BOOKING_CONFIRMED topic [{}]", eventId);

      return eventRepository.findById(eventId)
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .retryWhen(defaultRepositoryRetry)
          .filter(event -> event.getAvailableBookings() > 0)
          .switchIfEmpty(Mono.defer(() -> {
            log.warn("Event [{}] has no available bookings left. Sending message to BOOKING_EVENT_UNAVAILABLE topic",
                eventId);
            var buMsg = new BookingEventUnavailable(message.getBooking(), eventId);
            return kafkaSender.send(
                    createSenderMono(Topics.BOOKING_EVENT_UNAVAILABLE, eventId, buMsg, clock, extractMDCIntoHeaders(tracer)))
                .single()
                .timeout(timeoutDuration)
                .retryWhen(defaultKafkaRetry)
                .onErrorResume(err -> {
                  log.error("Unable to send message to BOOKING_EVENT_UNAVAILABLE topic. Manual intervention necessary",
                      err);
                  return Mono.empty();
                }).then(Mono.empty());
          }))
          .flatMap(event -> eventRepository.save(
                  event.withAvailableBookings(event.getAvailableBookings() - 1))
              .subscribeOn(Schedulers.boundedElastic())
              .timeout(timeoutDuration)
              .retryWhen(defaultRepositoryRetry)
              .map(evt -> record)
              .onErrorResume(err -> {
                log.error("BOOKING_CONFIRMED message not handled after max attempts. Sending to DLT",
                    err);
                return handleDLTLogic(record);
              })
          );
    } else {
      log.error("Unable to deserialize message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

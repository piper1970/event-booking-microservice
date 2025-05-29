package piper1970.bookingservice.kafka.listeners;

import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.bookingservice.repository.BookingSummary;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class EventChangedListener extends DiscoverableListener {

  public static final String SERVICE_NAME = "booking-service";
  private final KafkaSender<Integer, Object> kafkaSender;
  private final BookingRepository bookingRepository;
  private final Duration timeoutDuration;
  private final Retry defaultRepositoryRetry;
  private final Retry defaultKafkaRetry;
  private final Clock clock;
  private Disposable subscription;

  public EventChangedListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      KafkaSender<Integer, Object> kafkaSender,
      BookingRepository bookingRepository,
      @Value("${booking-repository.timout.milliseconds}") Long timeoutMillis,
      @Qualifier("repository") Retry defaultRepositoryRetry,
      @Qualifier("kafka") Retry defaultKafkaRetry,
      Clock clock
      ) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.kafkaSender = kafkaSender;
    this.bookingRepository = bookingRepository;
    timeoutDuration = Duration.ofMillis(timeoutMillis);
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
  protected String getTopic() {
    return Topics.EVENT_CHANGED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    log.debug("EventChangedListener::handleIndividualRequest started");
    if(record.value() instanceof EventChanged message) {
      var eventId = message.getEventId();
      log.debug(
          "[{}] message has been received EVENT_CHANGED topic. Relaying message to BOOKINGS_UPDATED topic with related bookings info",
          eventId);
      return bookingRepository.findByEventIdAndBookingStatusNotIn(eventId, List.of(
          BookingStatus.CANCELLED,
          BookingStatus.COMPLETED))
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .retryWhen(defaultRepositoryRetry)
          .map(this::toBookingId)
          .collectList()
          .doOnNext(bookings -> log.debug("[{}] bookings updated for event [{}]", bookings.size(), eventId))
          .flatMap(bookings -> {
            var buMsg = new BookingsUpdated();
            buMsg.setEventId(eventId);
            buMsg.setMessage(message.getMessage());
            buMsg.setBookings(bookings);
            return kafkaSender.send(createSenderMono(Topics.BOOKINGS_UPDATED, eventId, buMsg, clock))
                .subscribeOn(Schedulers.boundedElastic())
                .single()
                .timeout(timeoutDuration)
                .retryWhen(defaultKafkaRetry)
                .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
                .map(updatedBooking -> record);
          })
          .onErrorResume(err -> {
            log.error("Unable to send EventChanged message after max attempts. Sending to DLT", err);
            return handleDLTLogic(record);
          });

    }else{
      log.error("Unable to deserialize EventChanged message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }

  private BookingId toBookingId(BookingSummary summary) {
    return new BookingId(
        summary.getId(), summary.getEmail(), summary.getUsername()
    );
  }
}

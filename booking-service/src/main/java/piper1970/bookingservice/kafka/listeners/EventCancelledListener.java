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
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.EventCancelled;
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
public class EventCancelledListener extends DiscoverableListener {

  public static final String SERVICE_NAME = "booking-service";
  private final KafkaSender<Integer, Object> kafkaSender;
  private final BookingRepository bookingRepository;
  private final TransactionalOperator transactionalOperator;
  private final Duration timeoutDuration;
  private final Retry defaultRepositoryRetry;
  private Disposable subscription;
  private final Clock clock;

  public EventCancelledListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      KafkaSender<Integer, Object> kafkaSender,
      BookingRepository bookingRepository,
      TransactionalOperator transactionalOperator,
      @Value("${booking-repository.timout.milliseconds}") Long timeoutMillis,
      @Qualifier("repository") Retry defaultRepositoryRetry,
      Clock clock) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.kafkaSender = kafkaSender;
    this.bookingRepository = bookingRepository;
    this.transactionalOperator = transactionalOperator;
    timeoutDuration = Duration.ofMillis(timeoutMillis);
    this.defaultRepositoryRetry = defaultRepositoryRetry;
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
    return Topics.EVENT_CANCELLED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    log.debug("EventCancelledListener::handleIndividualRequest started");
    if(record.value() instanceof EventCancelled message) {
      var eventId = message.getEventId();
      log.debug(
          "[{}] message has been received from EVENT_CANCELLED topic.  Relaying message to BOOKINGS_CANCELLED topic with related bookings info",
          eventId);
      return bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId, List.of(
          BookingStatus.IN_PROGRESS,
          BookingStatus.CONFIRMED))
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .map(booking -> booking.withBookingStatus(BookingStatus.CANCELLED))
          .collectList()
          .flatMapMany(bookingList ->
              bookingRepository.saveAll(bookingList)
                  .subscribeOn(Schedulers.boundedElastic())
                  .timeout(timeoutDuration)
                  .doOnNext(updatedBooking -> log.info("Cancelled booking saved: [{}]", updatedBooking))
                  .map(this::toBookingId)
              )
          .collectList()
          .doOnNext(bookings -> log.debug("[{}] bookings cancelled for event [{}]", bookings.size(), eventId))
          .flatMap(bookings -> {
            var buMsg = new BookingsCancelled();
            buMsg.setEventId(eventId);
            buMsg.setMessage(message.getMessage());
            buMsg.setBookings(bookings);
            return kafkaSender.send(createSenderMono(Topics.BOOKINGS_CANCELLED, eventId, buMsg, clock))
                .subscribeOn(Schedulers.boundedElastic())
                .single()
                .timeout(timeoutDuration)
                .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
                .map(_senderResult -> record);
          })
          .as(transactionalOperator::transactional)
          .retryWhen(defaultRepositoryRetry)
          .onErrorResume(err -> {
            log.error("Unable to send EventCancelled message after max attempts. Sending to DLT and aborting transaction", err);
            return handleDLTLogic(record);
          });
    }else{
      log.error("Unable to deserialize EventCancelled message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }

  private BookingId toBookingId(Booking booking) {
    return new BookingId(
        booking.getId(), booking.getEmail(), booking.getUsername()
    );
  }
}

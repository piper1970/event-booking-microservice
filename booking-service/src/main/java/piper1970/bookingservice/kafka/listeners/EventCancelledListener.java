package piper1970.bookingservice.kafka.listeners;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
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
import reactor.util.retry.Retry;

@Component
@Slf4j
public class EventCancelledListener extends DiscoverableListener {

  public static final String SERVICE_NAME = "booking-service";
  private final ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate;
  private final BookingRepository bookingRepository;
  private final TransactionalOperator transactionalOperator;
  private Disposable subscription;

  public EventCancelledListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate,
      BookingRepository bookingRepository,
      TransactionalOperator transactionalOperator) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
    this.bookingRepository = bookingRepository;
    this.transactionalOperator = transactionalOperator;
  }

  @EventListener(ApplicationReadyEvent.class)
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
    if(record.value() instanceof EventCancelled message) {
      var eventId = message.getEventId();
      log.debug(
          "[{}] message has been received from EVENT_CANCELLED topic.  Relaying message to BOOKINGS_CANCELLED topic with related bookings info",
          eventId);
      return bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId, List.of(
          BookingStatus.CANCELLED,
          BookingStatus.COMPLETED))
          .subscribeOn(Schedulers.boundedElastic())
          .map(booking -> booking.withBookingStatus(BookingStatus.CANCELLED))
          .collectList()
          .publishOn(Schedulers.boundedElastic())
          .flatMapMany(bookingRepository::saveAll)
          .map(this::toBookingId)
          .collectList()
          .doOnNext(bookings -> log.debug("[{}] bookings cancelled for event [{}]", bookings.size(), eventId))
          .flatMap(bookings -> {
            var buMsg = new BookingsCancelled();
            buMsg.setEventId(eventId);
            buMsg.setMessage(message.getMessage());
            buMsg.setBookings(bookings);
            return reactiveKafkaProducerTemplate.send(Topics.BOOKINGS_CANCELLED, buMsg)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
                .doOnError(err -> log.error("Unable to send message to BOOKINGS_CANCELLED topic. Manual intervention necessary", err))
                .then(Mono.just(record));
          })
          .as(transactionalOperator::transactional)
          .retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
          .jitter(0.7D))
          .doOnError(err -> log.error("Unable to complete EventCancelled transaction", err))
          .onErrorResume(err -> handleDLTLogic(record));
    }else{
      log.error("Unable to unmarshal EventCancelled message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }

  private BookingId toBookingId(Booking booking) {
    return new BookingId(
        booking.getId(), booking.getEmail(), booking.getUsername()
    );
  }
}

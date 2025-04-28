package piper1970.bookingservice.kafka.listeners;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
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

@Component
@Slf4j
public class EventChangedListener extends DiscoverableListener {

  public static final String SERVICE_NAME = "booking-service";
  private final ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate;
  private final BookingRepository bookingRepository;
  private Disposable subscription;

  public EventChangedListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate,
      BookingRepository bookingRepository) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
    this.bookingRepository = bookingRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
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
    if(record.value() instanceof EventChanged message) {
      var eventId = message.getEventId();
      log.debug(
          "[{}] message has been received EVENT_CHANGED topic. Relaying message to BOOKINGS_UPDATED topic with related bookings info",
          eventId);
      return bookingRepository.findByEventIdAndBookingStatusNotIn(eventId, List.of(
          BookingStatus.CANCELLED,
          BookingStatus.COMPLETED))
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .map(this::toBookingId)
          .collectList()
          .doOnNext(bookings -> log.debug("[{}] bookings updated for event [{}]", bookings.size(), eventId))
          .flatMap(bookings -> {
            var buMsg = new BookingsUpdated();
            buMsg.setEventId(eventId);
            buMsg.setMessage(message.getMessage());
            buMsg.setBookings(bookings);
            return reactiveKafkaProducerTemplate.send(Topics.EVENT_CHANGED, buMsg)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
                .doOnError(err -> log.error("Unable to send message to EVENT_CHANGED topic. Manual intervention necessary", err))
                .then(Mono.just(record));
          })
          .onErrorResume(err -> handleDLTLogic(record));

    }else{
      log.error("Unable to unmarshal EventChanged message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }

  private BookingId toBookingId(BookingSummary summary) {
    return new BookingId(
        summary.getId(), summary.getEmail(), summary.getUsername()
    );
  }
}

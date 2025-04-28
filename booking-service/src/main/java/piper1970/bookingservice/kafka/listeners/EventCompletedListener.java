package piper1970.bookingservice.kafka.listeners;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.messages.EventCompleted;
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
public class EventCompletedListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private Disposable subscription;

  public EventCompletedListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      BookingRepository bookingRepository) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.bookingRepository = bookingRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeReceiverFlux() {
    subscription = buildFluxRequest()
        .subscribe(rcv -> rcv.receiverOffset().acknowledge());
  }

  @Override
  protected String getTopic() {
    return Topics.EVENT_COMPLETED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    if (record.value() instanceof EventCompleted message) {
      var eventId = message.getEventId();
      log.debug(
          "[{}] message has been received from EVENT_COMPLETED topic. Updating related bookings",
          eventId);

      return bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId, List.of(
              BookingStatus.CANCELLED, BookingStatus.COMPLETED, BookingStatus.IN_PROGRESS
          ))
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .map(booking -> booking.withBookingStatus(BookingStatus.COMPLETED))
          .collectList()
          .flatMapMany(bookingRepository::saveAll)
          .retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
              .jitter(0.7D))
          .count()
          .doOnNext(count -> log.info("{} Bookings completed for event {}", count, eventId))
          .doOnError(err -> log.error("EVENT_COMPLETED message not handled properly: [{}]",
              err.getMessage(), err))
          .map(cnt -> record)
          .onErrorResume(err -> handleDLTLogic(record));
    } else {
      log.error(
          "Unable to unmarshal EventCompleted message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

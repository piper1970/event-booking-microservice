package piper1970.bookingservice.kafka.listeners;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
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
public class BookingEventUnavailableListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private Disposable subscription;

  public BookingEventUnavailableListener(
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
    return Topics.BOOKING_EVENT_UNAVAILABLE;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    if(record.value() instanceof BookingEventUnavailable message) {
      return bookingRepository.findById(message.getBooking().getId())
          .subscribeOn(Schedulers.boundedElastic())
          .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus()
              || BookingStatus.CONFIRMED == booking.getBookingStatus())
          .flatMap(booking -> bookingRepository.save(booking.withBookingStatus(BookingStatus.CANCELLED))
              .subscribeOn(Schedulers.boundedElastic())
          ).doOnNext((Booking booking) ->
          log.warn("Booking [{}] for event [{}] has been cancelled due to unavailability",
              booking.getId(),
              booking.getEventId()))
          .retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
              .jitter(0.7D))
          .doOnError(err -> log.error("Unable to cancel booking in repository: [{}]", err.getMessage(), err))
          .map(booking -> record)
          .onErrorResume(err -> handleDLTLogic(record));
    }else{
      log.error("Unable to unmarshal BookingEventUnavailable message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

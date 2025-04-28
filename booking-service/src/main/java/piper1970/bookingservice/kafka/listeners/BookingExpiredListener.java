package piper1970.bookingservice.kafka.listeners;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingExpiredListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private Disposable subscription;

  public BookingExpiredListener(
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
    return Topics.BOOKING_EXPIRED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  // TODO: need timeout logic

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    if(record.value() instanceof BookingExpired message) {
      return bookingRepository.findById(message.getBooking().getId())
          .subscribeOn(Schedulers.boundedElastic())
          .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus())
          .flatMap(booking -> bookingRepository.save(booking.withBookingStatus(BookingStatus.CANCELLED))
              .subscribeOn(Schedulers.boundedElastic())
          ).doOnNext(updatedBooking ->
              log.info("Booking cancelled due to expired confirmation: {}", updatedBooking)
          )
          .retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
              .jitter(0.7D))
          .doOnError(throwable -> log.error("Unable to set booking status to cancelled: [{}]",
              throwable.getMessage(), throwable))
          .map(_evt -> record)
          .onErrorResume(err -> handleDLTLogic(record));
    }else{
      log.error("Unable to unmarshal BookingExpired message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

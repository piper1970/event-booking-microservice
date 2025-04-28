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
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingConfirmedListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private Disposable subscription;

  public BookingConfirmedListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      BookingRepository bookingRepository) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.bookingRepository = bookingRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeReceiverFlux() {
    subscription = buildFluxRequest()
        .subscribe(rec -> rec.receiverOffset().acknowledge());
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_CONFIRMED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    // TODO: need timeout logic
    if (record.value() instanceof BookingConfirmed message) {
      return bookingRepository.findById(message.getBooking().getId())
          .subscribeOn(Schedulers.boundedElastic())
          .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus())
          .flatMap(
              booking -> bookingRepository.save(booking.withBookingStatus(BookingStatus.CONFIRMED))
          ).doOnNext(updatedBooking -> log.info("Booking confirmed: {}", updatedBooking))
          .doOnError(
              throwable -> log.error("Booking confirmation failure: {}", throwable.getMessage(),
                  throwable))
          .retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
              .jitter(0.7D))
          .map(_evt -> record)
          .onErrorResume(err -> {
            log.error("BOOKING_CONFIRMED message not handled after max attempts. Sending to DLQ",
                err);
            return handleDLTLogic(record);
          });
    } else {
      log.error(
          "Unable to unmarshal BookingConfirmed message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

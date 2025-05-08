package piper1970.bookingservice.kafka.listeners;

import static piper1970.eventservice.common.kafka.KafkaHelper.DEFAULT_RETRY;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

@Component
@Slf4j
public class BookingConfirmedListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private final Duration timeoutDuration;
  private Disposable subscription;

  public BookingConfirmedListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      BookingRepository bookingRepository,
      @Value("${booking-repository.timout.milliseconds}") Long timeoutMillis) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.bookingRepository = bookingRepository;
    timeoutDuration = Duration.ofMillis(timeoutMillis);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Override
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
    log.debug("BookingConfirmedListener::handleIndividualRequest started");
    if (record.value() instanceof BookingConfirmed message) {
      return bookingRepository.findById(message.getBooking().getId())
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .timeout(timeoutDuration)
          .retryWhen(DEFAULT_RETRY)
          .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus())
          .flatMap(
              booking -> bookingRepository.save(booking.withBookingStatus(BookingStatus.CONFIRMED))
                  .subscribeOn(Schedulers.boundedElastic())
                  .log()
                  .timeout(timeoutDuration)
                  .retryWhen(DEFAULT_RETRY)
                  .doOnNext(updatedBooking -> log.info("Confirmed booking saved: [{}]", updatedBooking))
                  .map(updatedBooking -> record)
          )
          .onErrorResume(err -> {
            log.error("BookingConfirmed message not handled after max attempts. Sending to DLT",
                err);
            return handleDLTLogic(record);
          });
    } else {
      log.error(
          "Unable to deserialize BookingConfirmed message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

package piper1970.eventservice.kafka.listeners;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingCancelledListener extends DiscoverableListener{

  private final EventRepository eventRepository;
  private Disposable subscription;

  public BookingCancelledListener(ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      EventRepository eventRepository,
      DeadLetterTopicProducer deadLetterTopicProducer) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.eventRepository = eventRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeReceiverFlux() {
    subscription = buildFluxRequest()
        .subscribe(rec -> rec.receiverOffset().acknowledge());
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_CANCELLED;
  }

  /**
   * Helper method to update event count due to booking cancellation.
   * Retries up to 3 times, returning empty mono if not success
   *
   * @param record ReceiverRecord containing BookingCancelled message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(ReceiverRecord<Integer, Object> record){
    if(record.value() instanceof BookingCancelled message) {
      return eventRepository.findById(message.getEventId())
          .subscribeOn(Schedulers.boundedElastic())
          // TODO: handle timeout logic
          .flatMap(event -> eventRepository.save(event.withAvailableBookings(event.getAvailableBookings() + 1))
              .subscribeOn(Schedulers.boundedElastic())
          )
          .doOnNext((Event evt) -> log.info(
              "Event [{}] availabilities increased to [{}] due to booking cancellation",
              evt.getId(), evt.getAvailableBookings()))
          .doOnError(err -> log.error("Event [{}] for cancelled booking not properly updated", message.getEventId(), err))
          .retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
              .jitter(0.7D))
          .map(_evt -> record)
          .onErrorResume(err -> {
            log.error("BOOKING_CANCELLED message not handled after max attempts. Sending to DLQ",
                err);
            return handleDLTLogic(record);
          });
    }else{
      log.error("Unable to unmarshal message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}

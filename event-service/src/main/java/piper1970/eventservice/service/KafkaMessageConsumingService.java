package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageConsumingService implements MessageConsumingService {

  private final EventRepository eventRepository;
  private final KafkaTemplate<Integer, Object> kafkaTemplate;
  private static final String SERVICE_NAME = "event-service";

  @Override
  @KafkaListener(topics = Topics.BOOKING_CANCELLED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingCancelledMessage(BookingCancelled message) {
    var eventId = message.getEventId();
    log.debug("Consuming from BOOKING_CANCELLED topic [{}]", eventId);

    return eventRepository.findById(eventId)
        .flatMap(evt -> {
          var updatedEvent = evt.toBuilder()
              .availableBookings(evt.getAvailableBookings() + 1)
              .build();
          return eventRepository.save(updatedEvent);
        })
        .doOnNext((Event evt) ->
            log.info(
                "Event [{}] availabilities increased to [{}] due to cancellation of booking [{}]",
                eventId, evt.getAvailableBookings(), message.getBooking().getId())
        )
        .doOnError(err -> log.error("BOOKING_CANCELLED message not handled", err))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_CONFIRMED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message) {
    var eventId = message.getEventId();
    log.debug("Consuming from BOOKING_CONFIRMED topic [{}]", eventId);
    return eventRepository.findById(eventId)
        .filter(event -> event.getAvailableBookings() > 0)
        .switchIfEmpty(Mono.fromRunnable(() -> {
          log.warn("Event [{}] has no available bookings left. Sending message to BOOKING_EVENT_UNAVAILABLE topic", eventId);
          var buMsg = new BookingEventUnavailable(message.getBooking(), eventId);
          kafkaTemplate.send(Topics.BOOKING_EVENT_UNAVAILABLE, eventId, buMsg)
              .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
        }))
        .flatMap(event -> {
          var updatedEvent = event.toBuilder()
              .availableBookings(event.getAvailableBookings() - 1)
              .build();
          return eventRepository.save(updatedEvent);
        })
        .doOnError(err -> log.error("BOOKING_CONFIRMED message not handled", err))
        .then();
  }
}

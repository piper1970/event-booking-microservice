package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageConsumingService implements MessageConsumingService {

  private final EventRepository eventRepository;

  @Override
  @KafkaListener(topics = Topics.BOOKING_CANCELLED)
  public Mono<Void> consumeBookingCancelledMessage(BookingCancelled message) {

    var eventId = message.getEventId();
    var bookingId = message.getBooking().getId();

    return eventRepository.findById(eventId)
        .flatMap(evt -> {
          var updatedEvent = evt.toBuilder()
              .availableBookings(evt.getAvailableBookings() + 1)
              .build();
          return eventRepository.save(updatedEvent);
        })
        .doOnNext(evt ->
            log.info(
                "Event [{}] availabilities increased to [{}] due to cancellation of booking [{}]",
                eventId, evt.getAvailableBookings(), bookingId)
        )
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_CONFIRMED)
  @SendTo(Topics.BOOKING_EVENT_UNAVAILABLE)
  public Mono<BookingEventUnavailable> consumeBookingConfirmedMessage(BookingConfirmed message) {

    var eventId = message.getEventId();

    return eventRepository.findById(eventId)
        .flatMap(evt -> {

          if (evt.getAvailableBookings() > 0) {
            var updatedEvent = evt.toBuilder()
                .availableBookings(evt.getAvailableBookings() - 1)
                .build();
            return eventRepository.save(updatedEvent)
                .then(Mono.empty());
          } else {

            log.warn("Event [{}] has no available bookings left", evt.getId());

            var buMsg = new BookingEventUnavailable();
            buMsg.setEventId(eventId);
            buMsg.setBooking(message.getBooking());
            return Mono.just(buMsg);
          }
        });
  }
}

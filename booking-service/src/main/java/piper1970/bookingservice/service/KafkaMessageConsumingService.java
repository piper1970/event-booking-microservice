package piper1970.bookingservice.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaMessageConsumingService implements MessageConsumingService {

  private final BookingRepository bookingRepository;
  private final MessagePostingService messagePostingService;

  @Override
  @KafkaListener(topics = Topics.BOOKING_CONFIRMED)
  public Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message) {
    return bookingRepository.findById(message.getBooking().getId())
        .flatMap(booking -> {
          var updatedBooking = booking.toBuilder()
              .bookingStatus(BookingStatus.CONFIRMED)
              .build();
          return bookingRepository.save(updatedBooking);
        })
        .doOnNext(updatedBooking ->
            log.info("Booking confirmed: {}", updatedBooking)
        )
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE)
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {
    return bookingRepository.findById(message.getBooking().getId())
        .flatMap(booking -> {
          var updatedBooking = booking.toBuilder()
              .bookingStatus(BookingStatus.CANCELLED)
              .build();
          return bookingRepository.save(updatedBooking);
        })
        .doOnNext(booking ->
            log.warn("Booking [{}] for event [{}] has been cancelled due to unavailability",
                booking.getId(),
                booking.getEventId()))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CHANGED)
  public Mono<Void> consumeEventChangedMessage(EventChanged message) {
    var eventId = message.getEventId();
    return getBookingIds(eventId)
        .doOnNext(list -> {
          var buMsg = new BookingsUpdated();
          buMsg.setEventId(eventId);
          buMsg.setMessage(message.getMessage());
          buMsg.setBookings(list);
          messagePostingService.postBookingsUpdatedMessage(buMsg);
        })
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CANCELLED)
  public Mono<Void> consumeEventCancelledMessage(EventCancelled message) {
    var eventId = message.getEventId();
    return getBookingIds(eventId)
        .doOnNext(list -> {
          var bcMsg = new BookingsCancelled();
          bcMsg.setEventId(eventId);
          bcMsg.setMessage(message.getMessage());
          bcMsg.setBookings(list);
          messagePostingService.postBookingsCancelledMessage(bcMsg);
        }).then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_COMPLETED)
  public Mono<Void> consumeEventCompletedMessage(EventCompleted message) {

    // TODO: update all bookings for this event to completed

    return Mono.empty();
  }

  private Mono<List<BookingId>> getBookingIds(Integer eventId) {

    // return only IN_PROGRESS or CONFIRMED bookings
    return bookingRepository.findByEventIdAndBookingStatusNotIn(eventId,
        List.of(BookingStatus.CANCELLED,
            BookingStatus.COMPLETED))
        .map(summary -> {
          var bookingId = new BookingId();
          bookingId.setUsername(summary.getUsername());
          bookingId.setId(summary.getId());
          bookingId.setEmail(summary.getEmail());
          return bookingId;
        })
        .collectList();
  }
}

package piper1970.bookingservice.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;
import piper1970.bookingservice.domain.Booking;
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
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.eventservice.common.topics.Topics;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaMessageConsumingService implements MessageConsumingService {

  private final BookingRepository bookingRepository;

  // TODO: need to handle timeout behavior for calls to repository

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
  @KafkaListener(topics = Topics.BOOKING_EXPIRED)
  public Mono<Void> consumeBookingExpiredMessage(BookingExpired message) {
    return bookingRepository.findById(message.getBooking().getId())
        .flatMap(booking -> {
          var updatedBooking = booking.toBuilder()
              .bookingStatus(BookingStatus.CANCELLED)
              .build();
          return bookingRepository.save(updatedBooking);
        })
        .doOnNext(updatedBooking ->
            log.info("Booking cancelled due to expired confirmation: {}", updatedBooking)
        )
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE)
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {
    return bookingRepository.findById(message.getBooking().getId())
        .filter(booking -> !BookingStatus.COMPLETED.equals(booking.getBookingStatus()))
        .flatMap(booking -> {
          var updatedBooking = booking.toBuilder()
              .bookingStatus(BookingStatus.CANCELLED)
              .build();
          return bookingRepository.save(updatedBooking);
        })
        .doOnNext((Booking booking) ->
            log.warn("Booking [{}] for event [{}] has been cancelled due to unavailability",
                booking.getId(),
                booking.getEventId()))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CHANGED)
  @SendTo(Topics.BOOKINGS_UPDATED)
  public Mono<BookingsUpdated> consumeEventChangedMessage(EventChanged message) {
    var eventId = message.getEventId();
    return getBookingIdsForConfirmedOrInProgress(eventId)
        .map(list -> {
          var buMsg = new BookingsUpdated();
          buMsg.setEventId(eventId);
          buMsg.setMessage(message.getMessage());
          buMsg.setBookings(list);
          return buMsg;
        });
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CANCELLED)
  @SendTo(Topics.BOOKINGS_CANCELLED)
  public Mono<BookingsCancelled> consumeEventCancelledMessage(EventCancelled message) {
    var eventId = message.getEventId();

    return getBookingIdsForConfirmedOrInProgress(eventId)
        .map(bookings -> {
          var buMsg = new BookingsCancelled();
          buMsg.setEventId(eventId);
          buMsg.setMessage(message.getMessage());
          buMsg.setBookings(bookings);
          return buMsg;
        });
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_COMPLETED)
  public Mono<Void> consumeEventCompletedMessage(EventCompleted message) {
    return bookingRepository.completeConfirmedBookingsForEvent(message.getEventId())
        .doOnNext(count -> log.info("{} bookings have been completed for event {}", count, message.getEventId()))
        .then();
  }

  private Mono<List<BookingId>> getBookingIdsForConfirmedOrInProgress(Integer eventId) {

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

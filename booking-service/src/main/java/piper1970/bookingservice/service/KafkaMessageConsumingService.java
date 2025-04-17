package piper1970.bookingservice.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.bookingservice.repository.BookingSummary;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.eventservice.common.topics.Topics;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaMessageConsumingService implements MessageConsumingService {

  public static final String SERVICE_NAME = "booking-service";
  private final BookingRepository bookingRepository;
  private final KafkaTemplate<Integer, Object> kafkaTemplate;

  // TODO: need to handle timeout behavior for calls to repository
  //  and error handling logic, with possible DLQ behavior

  @Override
  @KafkaListener(topics = Topics.BOOKING_CONFIRMED,
  errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message) {
    return bookingRepository.findById(message.getBooking().getId())
        // avoid re-changing status if already confirmed/cancelled/completed
        .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus())
        .flatMap(booking -> {
          var updatedBooking = booking.toBuilder()
              .bookingStatus(BookingStatus.CONFIRMED)
              .build();
          return bookingRepository.save(updatedBooking);
        })
        .doOnNext(updatedBooking -> log.info("Booking confirmed: {}", updatedBooking)
        )
        .doOnError(
            throwable -> log.error("Booking confirmation failure: {}", throwable.getMessage(),
                throwable))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EXPIRED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingExpiredMessage(BookingExpired message) {
    return bookingRepository.findById(message.getBooking().getId())
        // avoid re-changing status if already confirmed/cancelled/completed
        .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus())
        .flatMap(booking -> {
          var updatedBooking = booking.toBuilder()
              .bookingStatus(BookingStatus.CANCELLED)
              .build();
          return bookingRepository.save(updatedBooking);
        })
        .doOnNext(updatedBooking ->
            log.info("Booking cancelled due to expired confirmation: {}", updatedBooking)
        ).doOnError(throwable -> log.error("Unable to set booking status to cancelled: [{}]",
            throwable.getMessage(), throwable))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {
    return bookingRepository.findById(message.getBooking().getId())
        // avoid unnecessary db writes
        .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus()
            || BookingStatus.CONFIRMED == booking.getBookingStatus())
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
        .doOnError(err -> log.error("Unable to cancel booking in repository: [{}]", err.getMessage(), err))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CHANGED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeEventChangedMessage(EventChanged message) {
    // Updates to event won't change state of bookings
    // unless it is a cancellation, which is handled by different topic
    var eventId = message.getEventId();
    log.debug(
        "[{}] message has been received EVENT_CHANGED topic. Relaying message to BOOKINGS_UPDATED topic with related bookings info",
        eventId);

    return getBookingIdsForConfirmedOrInProgress(eventId)
        .doOnNext(bookings -> {
          log.debug("[{}] bookings updated for event [{}]", bookings.size(), eventId);
          var buMsg = new BookingsUpdated();
          buMsg.setEventId(eventId);
          buMsg.setMessage(message.getMessage());
          buMsg.setBookings(bookings);
          kafkaTemplate.send(Topics.BOOKINGS_UPDATED, eventId, buMsg)
              .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
        })
        .doOnError(err ->
          log.error("EVENT_CHANGED message not handled properly: [{}]",err.getMessage(), err)
        )
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CANCELLED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeEventCancelledMessage(EventCancelled message) {
    var eventId = message.getEventId();
    log.debug(
        "[{}] message has been received from EVENT_CANCELLED topic.  Relaying message to BOOKINGS_CANCELLED topic with related bookings info",
        eventId);

    return bookingRepository.
        findBookingsByEventIdAndBookingStatusIn(eventId, List.of(
            BookingStatus.IN_PROGRESS, BookingStatus.CONFIRMED
        ))
        .map(booking -> booking.withBookingStatus(BookingStatus.CANCELLED))
        .collectList()
        .flatMapMany(bookingRepository::saveAll)
        .map(this::toBookingId)
        .collectList()
        .doOnNext(bookings -> {
          log.debug("[{}] bookings cancelled for event [{}]", bookings.size(), eventId);
          var buMsg = new BookingsCancelled();
          buMsg.setEventId(eventId);
          buMsg.setMessage(message.getMessage());
          buMsg.setBookings(bookings);
          kafkaTemplate.send(Topics.BOOKINGS_CANCELLED, eventId, buMsg)
              .whenComplete(KafkaHelper.postResponseConsumer(SERVICE_NAME, log));
        })
        .doOnError(err -> log.error("EVENT_CANCELLED message not handled: [{}]", err.getMessage(), err))
        .then();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_COMPLETED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeEventCompletedMessage(EventCompleted message) {
    var eventId = message.getEventId();
    log.debug(
        "[{}] message has been received from EVENT_COMPLETED topic. Updating related bookings",
        eventId);
    return bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId, List.of(
            BookingStatus.CANCELLED, BookingStatus.COMPLETED, BookingStatus.IN_PROGRESS
        ))
        .map(booking -> booking.withBookingStatus(BookingStatus.COMPLETED))
        .collectList()
        .flatMapMany(bookingRepository::saveAll)
        .count()
        .doOnNext(count -> log.info("{} Bookings completed for event {}", count, eventId))
        .doOnError(err -> log.error("EVENT_COMPLETED message not handled properly: [{}]", err.getMessage(), err))
        .then();
  }

  private Mono<List<BookingId>> getBookingIdsForConfirmedOrInProgress(Integer eventId) {
    // return only IN_PROGRESS or CONFIRMED bookings
    return bookingRepository.findByEventIdAndBookingStatusNotIn(eventId,
            List.of(BookingStatus.CANCELLED,
                BookingStatus.COMPLETED))
        .map(this::toBookingId)
        .collectList();
  }

  private BookingId toBookingId(Booking booking) {
    return new BookingId(
        booking.getId(), booking.getEmail(), booking.getUsername()
    );
  }

  private BookingId toBookingId(BookingSummary summary) {
    return new BookingId(
        summary.getId(), summary.getEmail(), summary.getUsername()
    );
  }
}

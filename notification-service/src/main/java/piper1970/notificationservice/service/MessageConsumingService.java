package piper1970.notificationservice.service;

import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import reactor.core.publisher.Mono;

public interface MessageConsumingService {
  Mono<Void> consumeBookingCreatedMessage(BookingCreated message);
  Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message);
  Mono<Void> consumeBookingCancelledMessage(BookingCancelled message);
  Mono<Void> consumeBookingsCancelledMessage(BookingsCancelled message);
  Mono<Void> consumeBookingsUpdatedMessage(BookingsUpdated message);
}

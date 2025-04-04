package piper1970.bookingservice.service;

import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.publisher.Mono;

public interface MessageConsumingService {
  Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message);
  Mono<Void> consumeBookingExpiredMessage(BookingExpired message);
  Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message);
  Mono<BookingsUpdated> consumeEventChangedMessage(EventChanged message);
  Mono<BookingsCancelled> consumeEventCancelledMessage(EventCancelled message);
  Mono<Void> consumeEventCompletedMessage(EventCompleted message);
}

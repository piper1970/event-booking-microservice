package piper1970.eventservice.service;

import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import reactor.core.publisher.Mono;

public interface MessageConsumingService {
  Mono<Void> consumeBookingCancelledMessage(BookingCancelled message);
  Mono<BookingEventUnavailable> consumeBookingConfirmedMessage(BookingConfirmed message);

}

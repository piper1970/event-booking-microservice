package piper1970.eventservice.service;

import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import reactor.core.publisher.Mono;

public interface MessageConsumingService {
  Mono<Void> consumeBookingCancelledMessage(BookingCancelled message);
  Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message);
}

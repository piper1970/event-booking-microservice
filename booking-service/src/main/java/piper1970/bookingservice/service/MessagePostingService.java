package piper1970.bookingservice.service;

import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import reactor.core.publisher.Mono;

public interface MessagePostingService {
  Mono<Void> postBookingCreatedMessage(BookingCreated message);
  Mono<Void> postBookingCancelledMessage(BookingCancelled message);
  Mono<Void> postBookingsUpdatedMessage(BookingsUpdated message);
  Mono<Void> postBookingsCancelledMessage(BookingsCancelled message);
}

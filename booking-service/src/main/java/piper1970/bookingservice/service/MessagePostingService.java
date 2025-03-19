package piper1970.bookingservice.service;

import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;

public interface MessagePostingService {
  void postBookingCreatedMessage(BookingCreated message);
  void postBookingCancelledMessage(BookingCancelled message);
  void postBookingsUpdatedMessage(BookingsUpdated message);
  void postBookingsCancelledMessage(BookingsCancelled message);
}

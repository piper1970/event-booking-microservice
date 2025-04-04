package piper1970.notificationservice.service;

import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;

public interface MessagePostingService {
  void postBookingConfirmedMessage(BookingConfirmed message);
  void postBookingExpiredMessage(BookingExpired message);
}

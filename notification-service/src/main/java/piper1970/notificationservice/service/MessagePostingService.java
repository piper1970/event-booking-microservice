package piper1970.notificationservice.service;

import piper1970.eventservice.common.notifications.messages.BookingConfirmed;

public interface MessagePostingService {
  void postBookingConfirmedMessage(BookingConfirmed message);
}

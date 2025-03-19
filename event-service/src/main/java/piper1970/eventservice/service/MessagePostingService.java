package piper1970.eventservice.service;

import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;

public interface MessagePostingService {
  void postBookingEventUnavailableMessage(BookingEventUnavailable message);
  void postEventCancelledMessage(EventCancelled message);
  void postEventChangedMessage(EventChanged message);
}

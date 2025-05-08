package piper1970.notificationservice.service;

import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.publisher.Mono;

public interface MessagePostingService {
  Mono<Void> postBookingConfirmedMessage(BookingConfirmed message);
  Mono<Void> postBookingExpiredMessage(BookingExpired message);
}

package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaMessageConsumingService implements MessageConsumingService {

  private final BookingRepository bookingRepository;

  @Override
  @KafkaListener(topics = Topics.BOOKING_CONFIRMED)
  public Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message) {
    // TODO: need to implement
    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE)
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {
    // TODO: need to implement
    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CHANGED)
  public Mono<Void> consumeEventChangedMessage(EventChanged message) {
    // TODO: need to implement
    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.EVENT_CANCELLED)
  public Mono<Void> consumeEventCancelledMessage(EventCancelled message) {
    // TODO: need to implement
    return Mono.empty();
  }
}

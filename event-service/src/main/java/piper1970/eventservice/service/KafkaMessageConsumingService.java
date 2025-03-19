package piper1970.eventservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageConsumingService implements MessageConsumingService {

  private final EventRepository eventRepository;

  @Override
  @KafkaListener(topics = Topics.BOOKING_CANCELLED)
  public Mono<Void> consumeBookingCancelledMessage(BookingCancelled message) {
    // TODO: implement logic
    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_CONFIRMED)
  public Mono<Void> consumeBookingConfirmedMessage(BookingConfirmed message) {
    // TODO: implement logic
    return Mono.empty();
  }
}

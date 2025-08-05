package piper1970.bookingservice.service;

import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;
import static piper1970.eventservice.common.kafka.reactive.TracingHelper.extractMDCIntoHeaders;

import brave.Tracer;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.kafka.KafkaHelper;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveKafkaMessagePostingService implements MessagePostingService {

  private final KafkaSender<Integer, Object> kafkaSender;
  private final Clock clock;
  private final Tracer tracer;
  private static final String SERVICE_NAME = "booking-service";

  @Override
  public Mono<Void> postBookingCreatedMessage(BookingCreated message) {
    return Mono.deferContextual(context -> {
      try {
        var key = message.getBooking().getId();
        log.debug("Posting BOOKING_CREATED message [{}]", key);
        return kafkaSender.send(
                createSenderMono(Topics.BOOKING_CREATED, key, message, clock,
                    extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting BookingCreated message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

  @Override
  public Mono<Void> postBookingCancelledMessage(BookingCancelled message) {
    return Mono.deferContextual(context -> {
      try {
        var key = message.getBooking().getId();
        log.debug("Posting BOOKING_CANCELLED message [{}]", key);
        return kafkaSender.send(
                createSenderMono(Topics.BOOKING_CANCELLED, key, message, clock,
                    extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting BookingCancelled message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

  @Override
  public Mono<Void> postBookingsUpdatedMessage(BookingsUpdated message) {
    return Mono.deferContextual(context -> {
      try {
        var key = message.getEventId();
        log.debug("Posting BOOKINGS_UPDATED message [{}]", key);
        return kafkaSender.send(
                createSenderMono(Topics.BOOKINGS_UPDATED, key, message, clock,
                    extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting BookingsUpdated message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

  @Override
  public Mono<Void> postBookingsCancelledMessage(BookingsCancelled message) {
    return Mono.deferContextual(context -> {
      try {
        var key = message.getEventId();
        log.debug("Posting BOOKINGS_CANCELLED message [{}]", key);
        return kafkaSender.send(
                createSenderMono(Topics.BOOKINGS_CANCELLED, key, message, clock,
                    extractMDCIntoHeaders(tracer)))
            .subscribeOn(Schedulers.boundedElastic())
            .single()
            .doOnNext(KafkaHelper.postReactiveOnNextConsumer(SERVICE_NAME, log))
            .doOnError(throwable -> log.error("Error sending BOOKINGS_CANCELLED message: {}",
                throwable.getMessage(), throwable))
            .then();
      } catch (Exception e) {
        log.error("Unknown error occurred while posting BookingsCancelled message to kafka: {}",
            e.getMessage(), e);
        return Mono.error(e);
      }
    });
  }

}

package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.bookingservice.domain.Booking;
import piper1970.eventservice.common.exceptions.BookingNotFoundException;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.status.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultBookingWebService implements BookingWebService {

  private final BookingRepository bookingRepository;

  private final EventRequestService eventRequestService;

  @Override
  public Flux<Booking> findAllBookings() {
    return bookingRepository.findAll();
  }

  @Override
  public Flux<Booking> findBookingsByUsername(String username) {
    return bookingRepository.findByUsername(username);
  }

  @Override
  public Mono<Booking> findBookingById(Integer id) {
    return bookingRepository.findById(id);
  }

  @Override
  public Mono<Booking> findBookingIdByIdAndUsername(Integer id, String username) {
    return bookingRepository.findBookingIdByIdAndUsername(id, username);
  }

  @Override
  public Mono<Booking> createBooking(Booking booking, String token) {

    // TODO: contact EventService to see if event is available and get the time
    return eventRequestService.requestEvent(booking.getEventId(), token)
        .filter(dto -> dto.getAvailableBookings() >= 1)
        .filter(dto -> EventStatus.AWAITING.name().equals(dto.getEventStatus()))
        .flatMap(dto -> {
          var adjustedBooking = booking.toBuilder()
              .id(null)
              .eventId(dto.getId())
              .eventDateTime(dto.getEventDateTime())
              .build();
          return bookingRepository.save(adjustedBooking);
        }).doOnNext(addedEvent -> {
          // TODO: need to send CREATED_BOOKING event
        });
  }

  @Transactional
  @Override
  public Mono<Booking> updateBooking(Booking booking) {
    // ensure event date time is in the future
    return bookingRepository.existsById(booking.getId())
        .filter(Boolean.TRUE::equals)
        .flatMap(b -> bookingRepository.save(booking))
        .doOnNext(updatedBooking -> {
          // TODO: Emit UpdatedBooking event to Kafka (booker, event??)
        })
        .switchIfEmpty(Mono.error(
            new BookingNotFoundException("Booking not found for id " + booking.getId())));
  }

  @Override
  public Mono<Void> cancelBooking(Integer id) {
    return bookingRepository.deleteById(id)
        .doOnSuccess(cancelledBooking -> {
          // TODO: Emit CancelBooking event to kafka (event, booker)
        });
  }

}

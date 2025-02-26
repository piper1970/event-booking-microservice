package piper1970.bookingservice.service;

import java.time.LocalDateTime;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingUpdateRequest;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.BookingNotFoundException;
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
  public Mono<Booking> createBooking(BookingCreateRequest createRequest, String token) {

    Predicate<EventDto> validEvent = dto ->
        dto.getAvailableBookings() >= 1
            && dto.getEventStatus().equals(EventStatus.AWAITING.name())
            && dto.getEventDateTime().isAfter(LocalDateTime.now());

    // TODO: contact EventService to see if event is available and get the time
    return eventRequestService.requestEvent(createRequest.getEventId(), token)
        .filter(validEvent)
        .flatMap(dto -> {
          var booking = Booking.builder()
              .eventId(createRequest.getEventId())
              .username(createRequest.getUsername())
              .eventDateTime(dto.getEventDateTime())
              .bookingStatus(BookingStatus.IN_PROGRESS)
              .build();
          return bookingRepository.save(booking);
        }).doOnNext(addedEvent -> {
          // TODO: need to send CREATED_BOOKING event
        });
  }

  @Transactional
  @Override
  public Mono<Booking> updateBooking(Integer id, BookingUpdateRequest updateRequest) {
    // ensure event date time is in the future
    return bookingRepository.findById(id)
        .map(entity -> {
          // update only can change status and/or time
          if(updateRequest.getBookingStatus() != null) {
            entity.setBookingStatus(BookingStatus.valueOf(updateRequest.getBookingStatus()));
          }
          if(updateRequest.getEventDateTime() != null) {
            entity.setEventDateTime(updateRequest.getEventDateTime());
          }
          return entity;
        })
        .flatMap(bookingRepository::save)
        .doOnNext(updatedBooking -> {
          // TODO: Emit UpdatedBooking event to Kafka (booker, event??)
        })
        .switchIfEmpty(Mono.error(
            new BookingNotFoundException("Booking not found for id " + id)));
  }

  @Override
  public Mono<Void> cancelBooking(Integer id) {
    return bookingRepository.deleteById(id)
        .doOnSuccess(cancelledBooking -> {
          // TODO: Emit CancelBooking event to kafka (event, booker)
        });
  }
}

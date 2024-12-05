package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.repository.BookingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultBookingService implements BookingService {

  private final BookingRepository bookingRepository;

  @Override
  public Flux<Booking> findAllBookings() {
    return bookingRepository.findAll();
  }

  @Override
  public Mono<Booking> findBookingById(Integer id) {
    return bookingRepository.findById(id);
  }

  @Override
  public Mono<Booking> createBooking(Booking booking) {
    // database generates id upon insert
    return bookingRepository.save(booking.withId(null));
  }

  @Transactional
  @Override
  public Mono<Booking> updateBooking(Booking booking) {
    return bookingRepository.findById(booking.getId())
        .flatMap(b -> bookingRepository.save(booking))
        .switchIfEmpty(Mono.error(new BookingNotFoundException("Booking not found for id " + booking.getId())));
  }

  @Override
  public Mono<Void> deleteBooking(Integer id) {
    return bookingRepository.deleteById(id);
  }

}

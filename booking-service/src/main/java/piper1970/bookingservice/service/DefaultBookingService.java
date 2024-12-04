package piper1970.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import piper1970.bookingservice.domain.Booking;
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

}

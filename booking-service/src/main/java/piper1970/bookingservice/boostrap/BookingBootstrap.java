package piper1970.bookingservice.boostrap;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.repository.BookingRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingBootstrap implements CommandLineRunner {

  private final BookingRepository bookingRepository;

  @Override
  public void run(String... args){
    bookingRepository.count()
        .filter(count -> count == 0) // only hydrate empty db
        .flatMapMany(ignored -> bookingRepository.saveAll(
            List.of(Booking.builder()
                    .eventId(1)
                    .username("test_member")
                    .eventDateTime(LocalDateTime.now().plusDays(2))
                    .build(),
                Booking.builder()
                    .eventId(2)
                    .username("test_member")
                    .eventDateTime(LocalDateTime.now().plusDays(3))
                    .build())
        )).subscribe(booking -> log.info(booking.toString()));
  }
}

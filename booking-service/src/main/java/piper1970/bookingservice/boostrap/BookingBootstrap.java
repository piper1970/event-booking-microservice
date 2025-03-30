package piper1970.bookingservice.boostrap;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class BookingBootstrap implements CommandLineRunner {

  private final BookingRepository bookingRepository;

  private final Clock clock;

  @Override
  public void run(String... args){
    bookingRepository.count()
        .filter(count -> count == 0) // only hydrate empty db
        .flatMapMany(ignored -> bookingRepository.saveAll(
            List.of(Booking.builder()
                    .eventId(1)
                    .username("test-member")
                    .email("test-member@whatever.com")
                    .bookingStatus(BookingStatus.CONFIRMED)
                    .build(),
                Booking.builder()
                    .eventId(2)
                    .username("test-member")
                    .email("test-member@whatever.com")
                    .bookingStatus(BookingStatus.IN_PROGRESS)
                    .build())
        )).subscribe(booking -> log.info(booking.toString()));
  }
}

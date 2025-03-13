package piper1970.bookingservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;


@SpringBootTest
@Import(BookingServiceTestConfiguration.class)
class BookingServiceApplicationTests {

  @Test
  void contextLoads() {
  }

}

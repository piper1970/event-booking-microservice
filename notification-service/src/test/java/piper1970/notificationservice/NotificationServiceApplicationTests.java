package piper1970.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestNotificationsConfiguration.class)
@SpringBootTest
class NotificationServiceApplicationTests {

  @Test
  void contextLoads() {
  }

}

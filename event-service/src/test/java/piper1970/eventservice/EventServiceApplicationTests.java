package piper1970.eventservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(EventServiceTestConfiguration.class)
class EventServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

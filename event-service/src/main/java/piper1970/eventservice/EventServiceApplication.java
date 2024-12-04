package piper1970.eventservice;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;

@SpringBootApplication
@EnableR2dbcRepositories
@RequiredArgsConstructor
public class EventServiceApplication {

	private final EventRepository eventRepository;

	public static void main(String[] args) {
		SpringApplication.run(EventServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			eventRepository.saveAll(
					List.of(Event.builder()
									.title("Event1")
									.description("Description1")
									.location("Location1")
									.eventDateTime(LocalDateTime.now().plusDays(2))
							.build(), Event.builder()
							.title("Event2")
							.description("Description2")
							.location("Location2")
							.eventDateTime(LocalDateTime.now().plusDays(4))
							.build(),
							Event.builder()
									.title("Event3")
									.description("Description3")
									.location("Location3")
									.eventDateTime(LocalDateTime.now().plusDays(6))
									.build())
			).subscribe(System.out::println);
		};
	}

}

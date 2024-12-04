package piper1970.customerservice;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import piper1970.customerservice.domain.Customer;
import piper1970.customerservice.repository.CustomerRepository;

@SpringBootApplication
@EnableR2dbcRepositories
@RequiredArgsConstructor
public class CustomerServiceApplication {

  private final CustomerRepository customerRepository;

  public static void main(String[] args) {
    SpringApplication.run(CustomerServiceApplication.class, args);
  }

  @Bean
  public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
    return args -> {
      customerRepository.saveAll(
          List.of(
              Customer.builder()
                  .firstName("first1")
                  .lastName("last1")
                  .build(),
              Customer.builder()
                  .firstName("first2")
                  .lastName("last2")
                  .build(),
              Customer.builder()
                  .firstName("first3")
                  .lastName("last3")
                  .build(),
              Customer.builder()
                  .firstName("first4")
                  .lastName("last4")
                  .build()
          )
      ).subscribe();
    };
  }

}

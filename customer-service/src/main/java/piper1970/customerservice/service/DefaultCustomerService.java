package piper1970.customerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import piper1970.customerservice.domain.Customer;
import piper1970.customerservice.repository.CustomerRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultCustomerService implements CustomerService {

  private final CustomerRepository customerRepository;

  @Override
  public Flux<Customer> getUsers() {
    return customerRepository.findAll();
  }

  @Override
  public Mono<Customer> getUser(Integer id) {
    return customerRepository.findById(id);
  }
}

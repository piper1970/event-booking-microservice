package piper1970.customerservice.service;

import piper1970.customerservice.domain.Customer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerService {
  Flux<Customer> getUsers();
  Mono<Customer> getUser(Integer id);
}

package piper1970.customerservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.customerservice.domain.Customer;
import piper1970.customerservice.service.CustomerService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

  private final CustomerService customerService;

  @GetMapping
  public Flux<Customer> getCustomers() {
    return customerService.getUsers();
  }

  @GetMapping("{id}")
  public Mono<Customer> getCustomer(@PathVariable("id") Integer id) {
    return customerService.getUser(id);
  }

}

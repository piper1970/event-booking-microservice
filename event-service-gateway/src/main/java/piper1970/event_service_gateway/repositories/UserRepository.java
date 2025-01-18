package piper1970.event_service_gateway.repositories;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import piper1970.event_service_gateway.domain.User;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
  Mono<User> findByUsername(String username);
}

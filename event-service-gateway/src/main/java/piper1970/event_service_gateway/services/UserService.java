package piper1970.event_service_gateway.services;


import piper1970.event_service_gateway.domain.User;
import piper1970.event_service_gateway.model.UserSignup;
import reactor.core.publisher.Mono;

public interface UserService {
  Mono<User> save(User user);
  Mono<User> findByUsername(String username);

  boolean doPasswordsMatch(UserSignup signup);

  User fromSignup(UserSignup signup);

}

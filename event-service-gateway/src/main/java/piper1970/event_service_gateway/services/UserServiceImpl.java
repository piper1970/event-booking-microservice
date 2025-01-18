package piper1970.event_service_gateway.services;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import piper1970.event_service_gateway.domain.User;
import piper1970.event_service_gateway.model.UserSignup;
import piper1970.event_service_gateway.repositories.UserRepository;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public Mono<User> save(User user) {
    user.setPassword(passwordEncoder.encode(user.getPassword()));
    return userRepository.save(user);
  }

  @Override
  public Mono<User> findByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  @Override
  public boolean doPasswordsMatch(@NonNull UserSignup signup) {
    return signup.getPassword().equals(signup.getConfirmPassword());
  }

  @Override
  public User fromSignup(UserSignup signup) {
    return User.builder()
        .username(signup.getUsername())
        .password(signup.getPassword())
        .build();
  }

  // this should send a message to member service to add new member
}

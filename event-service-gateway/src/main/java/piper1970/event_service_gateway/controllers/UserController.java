package piper1970.event_service_gateway.controllers;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.event_service_gateway.model.AuthenticationRequest;
import piper1970.event_service_gateway.model.AuthenticationResponse;
import piper1970.event_service_gateway.model.UserSignup;
import piper1970.event_service_gateway.services.UserService;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;


  @PostMapping("/signup")
  public Mono<ResponseEntity<String>> signup(@Valid @RequestBody UserSignup userSignup) {
    if (!userService.doPasswordsMatch(userSignup)){
      return Mono.just(ResponseEntity.badRequest().body("Passwords do not match"));
    }else{
      return userService.findByUsername(userSignup.getUsername())
          // return bad request if user already present
          .map(ignored -> ResponseEntity.badRequest().body("User already exists"))
          // if user can't be found, new user can be saved
          // use Mono.defer for lazy evaluation of save command
          .switchIfEmpty(Mono.defer(() -> userService.save(userService.fromSignup(userSignup))
                  .map(user -> ResponseEntity.created(URI.create("/user/" + user.getId())).build())));
    }
  }

  @PostMapping("/login")
  public Mono<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
    return Mono.empty();
  }

  // how do we revalidate tokens...
}

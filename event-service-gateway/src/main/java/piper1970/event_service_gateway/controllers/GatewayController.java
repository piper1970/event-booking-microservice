package piper1970.event_service_gateway.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class GatewayController {

//  private final UserService userService;


//  @PostMapping("/signup")
//  public Mono<ResponseEntity<String>> signup(@Valid @RequestBody UserSignup userSignup) {
//    if (!userService.doPasswordsMatch(userSignup)){
//      return Mono.just(ResponseEntity.badRequest().body("Passwords do not match"));
//    }else{
//      return userService.findByUsername(userSignup.getUsername())
//          // return bad request if user already present
//          .map(ignored -> ResponseEntity.badRequest().body("User already exists"))
//          // if user can't be found, new user can be saved
//          // use Mono.defer for lazy evaluation of save command
//          .switchIfEmpty(Mono.defer(() -> userService.save(userService.fromSignup(userSignup))
//                  .map(user -> ResponseEntity.created(URI.create("/user/" + user.getId())).build())));
//    }
//  }

//  @PostMapping("/login")
//  public Mono<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
//    return Mono.empty();
//  }


  @GetMapping("/token")
  @PreAuthorize("hasRole('MEMBER')")
  public Mono<String> getHome(@RegisteredOAuth2AuthorizedClient("event-service-client") OAuth2AuthorizedClient authorizedClient,
      @AuthenticationPrincipal OidcUser user) {
    return Mono.just(authorizedClient.getAccessToken().getTokenValue());
  }

  // how do we revalidate tokens...
}

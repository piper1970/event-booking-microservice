package piper1970.api_gateway.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/testme")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ApiGateway Controller")
public class TestController {

  @GetMapping()
  @PreAuthorize("hasAuthority('MEMBER')") // need to ensure proper user
  public Mono<String> testMe() {
    return Mono.just("You have passed the test");
  }
}

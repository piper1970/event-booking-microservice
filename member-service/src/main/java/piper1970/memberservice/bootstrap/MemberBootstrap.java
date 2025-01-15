package piper1970.memberservice.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import piper1970.memberservice.domain.Member;
import piper1970.memberservice.repository.MemberRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberBootstrap implements CommandLineRunner {

  private final MemberRepository memberRepository;

  @Override
  public void run(String... args){
    memberRepository.count()
        .filter(m -> m == 0)
        .flatMapMany(m -> memberRepository.saveAll(
            List.of(
                Member.builder()
                    .username("user1")
                    .password("password1")
                    .firstName("first1")
                    .lastName("last1")
                    .email("email1@example.com")
                    .build(),
                Member.builder()
                    .username("user2")
                    .password("password2")
                    .firstName("first2")
                    .lastName("last2")
                    .email("email2@example.com")
                    .build(),
                Member.builder()
                    .username("user3")
                    .password("password3")
                    .firstName("first3")
                    .lastName("last3")
                    .email("email3@example.com")
                    .build(),
                Member.builder()
                    .username("user4")
                    .password("password4")
                    .firstName("first4")
                    .lastName("last4")
                    .email("email4@example.com")
                    .build()
            )
        )).subscribe(event -> log.info(event.toString()));
  }
}

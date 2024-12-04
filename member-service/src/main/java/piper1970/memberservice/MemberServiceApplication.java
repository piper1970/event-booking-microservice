package piper1970.memberservice;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import piper1970.memberservice.domain.Member;
import piper1970.memberservice.repository.MemberRepository;

@SpringBootApplication
@EnableR2dbcRepositories
@RequiredArgsConstructor
public class MemberServiceApplication {

  private final MemberRepository memberRepository;

  public static void main(String[] args) {
    SpringApplication.run(MemberServiceApplication.class, args);
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return args -> memberRepository.saveAll(
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
    ).subscribe();
  }

}

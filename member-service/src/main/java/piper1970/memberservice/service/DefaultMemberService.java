package piper1970.memberservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import piper1970.memberservice.domain.Member;
import piper1970.memberservice.repository.MemberRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultMemberService implements MemberService {

  private final MemberRepository memberRepository;

  @Override
  public Flux<Member> getMembers() {
    return memberRepository.findAll();
  }

  @Override
  public Mono<Member> getMember(Integer id) {
    return memberRepository.findById(id);
  }
}

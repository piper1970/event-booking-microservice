package piper1970.memberservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.memberservice.domain.Member;
import piper1970.memberservice.exceptions.MemberNotFoundException;
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

  @Override
  public Mono<Member> createMember(Member member) {
    // database generates id upon insert
    return memberRepository.save(member.withId(null));
  }

  @Transactional
  @Override
  public Mono<Member> updateMember(Member member) {
    return memberRepository.findById(member.getId())
        .flatMap(m -> memberRepository.save(member))
        .switchIfEmpty(Mono.error(new MemberNotFoundException("Member not found with id: " + member.getId())));
  }

  @Override
  public Mono<Void> deleteMember(Integer id) {
    // TODO: need to check bookings to remove components
    return memberRepository.deleteById(id);
  }
}

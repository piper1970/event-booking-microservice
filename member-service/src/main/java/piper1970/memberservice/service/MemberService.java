package piper1970.memberservice.service;

import piper1970.memberservice.domain.Member;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MemberService {
  Flux<Member> getMembers();
  Mono<Member> getMember(Integer id);
}

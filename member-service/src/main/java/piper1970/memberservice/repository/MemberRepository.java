package piper1970.memberservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.memberservice.domain.Member;

@Repository
public interface MemberRepository extends ReactiveCrudRepository<Member, Integer> {
}

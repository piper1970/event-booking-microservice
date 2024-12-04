package piper1970.memberservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import piper1970.memberservice.dto.mapper.MemberMapper;
import piper1970.memberservice.dto.model.MemberDto;
import piper1970.memberservice.service.MemberService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/v1/members")
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;
  private final MemberMapper memberMapper;

  @GetMapping
  public Flux<MemberDto> getMembers() {
    return memberService.getMembers()
        .map(memberMapper::toDto);
  }

  @GetMapping("{id}")
  public Mono<MemberDto> getMember(@PathVariable("id") Integer id) {
    return memberService.getMember(id)
        .map(memberMapper::toDto);
  }

}

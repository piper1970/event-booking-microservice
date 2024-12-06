package piper1970.memberservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.memberservice.dto.mapper.MemberMapper;
import piper1970.memberservice.dto.model.MemberDto;
import piper1970.memberservice.exceptions.MemberNotFoundException;
import piper1970.memberservice.service.MemberService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;
  private final MemberMapper memberMapper;

  @GetMapping
  public Flux<MemberDto> getMembers() {
    return memberService.getMembers()
        .map(memberMapper::toDto);
  }

  @GetMapping("/{id}")
  public Mono<MemberDto> getMember(@PathVariable("id") Integer id) {
    return memberService.getMember(id)
        .map(memberMapper::toDto)
        .switchIfEmpty(Mono.error(new MemberNotFoundException("Member with id " + id + " not found")));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<MemberDto> createMember(@Valid @RequestBody MemberDto memberDto) {
    return memberService.createMember(memberMapper.toEntity(memberDto))
        .map(memberMapper::toDto);
  }

  @PutMapping("/{id}")
  public Mono<MemberDto> updateMember(@PathVariable Integer id, @Valid @RequestBody MemberDto memberDto) {
    return memberService.updateMember(memberMapper.toEntity(memberDto).withId(id))
        .map(memberMapper::toDto);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> deleteMember(@PathVariable Integer id) {
    return memberService.deleteMember(id);
  }
}

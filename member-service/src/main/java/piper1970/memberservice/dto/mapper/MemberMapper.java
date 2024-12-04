package piper1970.memberservice.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import piper1970.memberservice.domain.Member;
import piper1970.memberservice.dto.model.MemberDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {
  Member toEntity(MemberDto dto);
  MemberDto toDto(Member entity);
}

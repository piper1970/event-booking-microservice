package piper1970.eventservice.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.dto.model.EventDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

  EventDto toDto(Event event);

  Event toEntity(EventDto eventDto);
}

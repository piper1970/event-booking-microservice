package piper1970.eventservice.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import piper1970.eventservice.common.events.status.EventStatus;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventStatusMapper {

  @ValueMappings({
      @ValueMapping(source="AWAITING", target="AWAITING"),
      @ValueMapping(source="COMPLETED", target="COMPLETED"),
      @ValueMapping(source="IN_PROGRESS", target="IN_PROGRESS"),
      @ValueMapping(source = MappingConstants.NULL, target = "AWAITING"),
      @ValueMapping(source = MappingConstants.ANY_UNMAPPED, target = "AWAITING")
  })
  EventStatus toEventStatus(String status);

  @ValueMappings({
      @ValueMapping(source="AWAITING", target="AWAITING"),
      @ValueMapping(source="COMPLETED", target="COMPLETED"),
      @ValueMapping(source="IN_PROGRESS", target="IN_PROGRESS")
  })
  String fromEventStatus(EventStatus status);

}

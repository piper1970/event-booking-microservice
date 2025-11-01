package piper1970.bookingservice.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import piper1970.bookingservice.domain.BookingStatus;

@Mapper(componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookingStatusMapper {

  @ValueMappings({
      @ValueMapping(source="IN_PROGRESS", target="IN_PROGRESS"),
      @ValueMapping(source="CONFIRMED", target="CONFIRMED"),
      @ValueMapping(source="CANCELLED", target="CANCELLED"),
      @ValueMapping(source="COMPLETED", target="COMPLETED"),
      @ValueMapping(source = MappingConstants.NULL, target="CANCELLED"),
      @ValueMapping(source = MappingConstants.ANY_UNMAPPED, target="CANCELLED")
  })
  BookingStatus toBookingStatus(String status);

  @ValueMappings({
      @ValueMapping(source="IN_PROGRESS", target="IN_PROGRESS"),
      @ValueMapping(source="CONFIRMED", target="CONFIRMED"),
      @ValueMapping(source="CANCELLED", target="CANCELLED"),
      @ValueMapping(source="COMPLETED", target="COMPLETED")
  })
  String fromBookingStatus(BookingStatus bookingStatus);
}

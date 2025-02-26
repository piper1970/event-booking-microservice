package piper1970.bookingservice.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.dto.model.BookingDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {BookingStatusMapper.class})
public interface BookingMapper {
  BookingDto entityToDto(Booking booking);
}

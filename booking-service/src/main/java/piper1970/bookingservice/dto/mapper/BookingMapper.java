package piper1970.bookingservice.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.dto.model.BookingDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookingMapper {
  Booking toEntity(BookingDto bookingDto);
  BookingDto toDto(Booking booking);
}

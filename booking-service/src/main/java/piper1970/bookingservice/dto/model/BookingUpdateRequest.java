package piper1970.bookingservice.dto.model;

import jakarta.validation.constraints.Future;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.eventservice.common.validation.annotations.EnumValues;
import piper1970.eventservice.common.validation.annotations.NullOrNotBlank;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class BookingUpdateRequest {

  @Future(message = "[eventDateTime] must be in the future")
  private LocalDateTime eventDateTime;

  @NullOrNotBlank(message = "[status] field cannot be blank, but may be omitted")
  @EnumValues(enumClass = BookingStatus.class, message = "[status] must be either 'IN_PROGRESS', 'CONFIRMED', or 'CANCELLED'")
  private String bookingStatus;

}

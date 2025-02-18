package piper1970.bookingservice.dto.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.eventservice.common.validation.annotations.EnumValues;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class BookingDto {
  private Integer id;

  @Positive
  @NotNull
  private Integer eventId;

  @NotBlank(message = "[username] field cannot be blank")
  @Size(max = 60, message = "[username] field must be within 60 character range")
  private String username;

  // will be set via eventservice
  private LocalDateTime eventDateTime;

  @EnumValues(enumClass = BookingStatus.class, message = "[status] must be either 'IN_PROGRESS', 'CONFIRMED', or 'CANCELLED'")
  private String bookingStatus;

}

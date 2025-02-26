package piper1970.eventservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.validation.annotations.EnumValues;
import piper1970.eventservice.common.validation.annotations.NullOrNotBlank;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class EventUpdateRequest {

  @NullOrNotBlank(message = "[title] field cannot be empty, but may be omitted")
  @Size(max = 255, message = "[title] field must be within 255 character range")
  private String title;

  @NullOrNotBlank(message = "[description] field cannot be blank, but may be omitted")
  @Size(max = 255, message = "[description] field must be within 255 character range")
  private String description;

  @NullOrNotBlank(message = "[location] field cannot be empty, but may be omitted")
  @Size(max = 255, message = "[location] field must be within 255 character range")
  private String location;

  @Future(message = "[eventDateTime] field must be a future date")
  private LocalDateTime eventDateTime;

  @DecimalMin(value = "0.0") // positive-or-zero
  @Digits(integer = 4, fraction = 2) // numeric(6,2)
  private BigDecimal cost;

  @PositiveOrZero(message = "[availableBookings] field cannot be negative")
  @Max(value=32767) // smallint
  private Integer availableBookings;

  @NullOrNotBlank(message = "[eventStatus] field cannot be empty, but may be omitted")
  @EnumValues(enumClass = EventStatus.class, message = "[eventStatus] field must be either 'AWAITING', 'IN_PROGRESS', 'COMPLETED', or 'CANCELLED'")
  private String eventStatus;

}

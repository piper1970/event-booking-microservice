package piper1970.eventservice.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import piper1970.eventservice.common.validation.annotations.CustomFuture;
import piper1970.eventservice.common.validation.annotations.NullOrNotBlank;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString
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

  @CustomFuture(message = "[eventDateTime] field must be a future date")
  private LocalDateTime eventDateTime;

  @Min(value = 30, message = "[durationInMinutes] field must be at least 30 minutes")
  private Integer durationInMinutes;

  @PositiveOrZero(message = "[availableBookings] field cannot be negative")
  @Max(value=32767) // smallint
  private Integer availableBookings;

}

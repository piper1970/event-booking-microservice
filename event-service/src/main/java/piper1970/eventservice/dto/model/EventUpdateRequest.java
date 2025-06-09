package piper1970.eventservice.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Update Event Request")
public class EventUpdateRequest {

  @Schema(
      description = "Event Title",
      nullable = true
  )
  @NullOrNotBlank(message = "[title] field cannot be empty, but may be omitted")
  @Size(max = 255, message = "[title] field must be within 255 character range")
  private String title;

  @Schema(
      description = "Short description of event",
      nullable = true
  )
  @NullOrNotBlank(message = "[description] field cannot be blank, but may be omitted")
  @Size(max = 255, message = "[description] field must be within 255 character range")
  private String description;

  @Schema(
      description = "Location of event",
      nullable = true
  )
  @NullOrNotBlank(message = "[location] field cannot be empty, but may be omitted")
  @Size(max = 255, message = "[location] field must be within 255 character range")
  private String location;

  @Schema(
      description = "date and time of event (must be a future date and time)",
      nullable = true
  )
  @CustomFuture(message = "[eventDateTime] field must be a future date")
  private LocalDateTime eventDateTime;

  @Schema(
      description = "duration of event, in minutes",
      nullable = true
  )
  @Min(value = 30, message = "[durationInMinutes] field must be at least 30 minutes")
  private Integer durationInMinutes;

  @Schema(
      description = "number of slots available for booking (cannot be negative)",
      nullable = true
  )
  @PositiveOrZero(message = "[availableBookings] field cannot be negative")
  @Max(value=32767) // smallint
  private Integer availableBookings;

}

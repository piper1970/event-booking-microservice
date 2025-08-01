package piper1970.eventservice.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import piper1970.eventservice.common.validation.annotations.CustomFuture;
import piper1970.eventservice.common.validation.annotations.NullOrNotBlank;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString
@With
@Schema(description = "Create Event Request")
public class EventCreateRequest {

  @Schema(
      description = "Creator of event, taken from security token",
      accessMode = Schema.AccessMode.READ_ONLY,
      nullable = true
  )
  @Null(message = "[facilitator] cannot be set within JSON. It is injected from authentication token")
  private String facilitator;

  @Schema(
      description = "Event Title"
  )
  @NotBlank(message = "[title] field cannot be empty")
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
      description = "Location of event"
  )
  @NotBlank(message = "[location] field cannot be empty")
  @Size(max = 255, message = "[location] field must be within 255 character range")
  private String location;

  @Schema(
      description = "date and time of event"
  )
  @NotNull(message = "[eventDateTime] field must be present and non-null")
  @CustomFuture(message = "[eventDateTime] field must be a future date")
  private LocalDateTime eventDateTime;

  @Schema(
      description = "duration of event, in minutes"
  )
  @NotNull(message = "[durationInMinutes] field must be present and non-null")
  @Min(value = 30, message = "[durationInMinutes] field must be at least 30 minutes")
  private Integer durationInMinutes;

  @Schema(
      description = "number of slots available for booking"
  )
  @NotNull(message = "[availableBookings] field must be present and non-null")
  @PositiveOrZero(message = "[availableBookings] field cannot be negative")
  @Max(value=32767) // smallint
  private Integer availableBookings;

}

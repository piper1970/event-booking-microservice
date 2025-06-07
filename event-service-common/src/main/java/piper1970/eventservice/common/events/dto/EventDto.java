package piper1970.eventservice.common.events.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import piper1970.eventservice.common.events.status.EventStatus;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString
@With
@Schema(description = "Event")
public class EventDto {

  @ToString.Exclude
  @Schema(
      description = "Event ID"
  )
  private Integer id;

  @Schema(
      description = "Creator of event"
  )
  private String facilitator;

  @Schema(
      description = "Event Title"
  )
  private String title;

  @Schema(
      description = "Short description of event"
  )
  private String description;

  @Schema(
      description = "Location of event"
  )
  private String location;

  @Schema(
      description = "status of event",
      allowableValues = {"AWAITING", "IN_PROGRESS", "CANCELLED", "COMPLETED"}
  )
  private EventStatus eventStatus;

  @Schema(
      description = "date and time of event"
  )
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  private LocalDateTime eventDateTime;

  @Schema(
      description = "duration of event, in minutes"
  )
  private Integer durationInMinutes;

  @Schema(
      description = "number of slots available for booking"
  )
  private Integer availableBookings;
}

package piper1970.eventservice.common.events.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class EventDto {

  @ToString.Exclude
  private Integer id;
  private String facilitator;
  private String title;
  private String description;
  private String location;
  private EventStatus eventStatus;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  private LocalDateTime eventDateTime;
  private Integer durationInMinutes;
  private Integer availableBookings;
}

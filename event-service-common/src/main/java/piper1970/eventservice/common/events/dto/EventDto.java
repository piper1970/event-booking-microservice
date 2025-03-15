package piper1970.eventservice.common.events.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@With
public class EventDto {
  private Integer id;
  private String facilitator;
  private String title;
  private String description;
  private String location;
  private boolean cancelled;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  private LocalDateTime eventDateTime;
  private Integer durationInMinutes;
  private BigDecimal cost;
  private Integer availableBookings;
}

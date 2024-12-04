package piper1970.eventservice.dto.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class EventDto {
  private Integer id;
  private String title;
  private String description;
  private String location;
  private LocalDateTime eventDateTime;
  private BigDecimal cost;
  private Integer availableBookings;
}

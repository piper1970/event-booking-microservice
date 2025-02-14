package piper1970.eventservice.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@With
@Table(name="events", schema="event_service")
public class Event {

  @Id
  private Integer id;

  private String facilitator;
  private String title;
  private String description;
  private String location;
  private LocalDateTime eventDateTime;
  private BigDecimal cost;
  private Integer availableBookings;
  private EventStatus eventStatus;

  @CreatedDate
  private LocalDateTime createdDateTime;

  @LastModifiedDate
  private LocalDateTime updatedDateTime;
}

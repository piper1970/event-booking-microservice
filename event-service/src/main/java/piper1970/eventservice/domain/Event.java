package piper1970.eventservice.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Builder(toBuilder = true)
@AllArgsConstructor
@With
@Table(name="events", schema="event_service")
public class Event {

  @Id
  @EqualsAndHashCode.Exclude
  private Integer id;

  private String facilitator;
  private String title;
  private String description;
  private String location;
  private LocalDateTime eventDateTime;
  private Integer durationInMinutes;
  private Integer availableBookings;
  private boolean cancelled;

  @CreatedDate
  private LocalDateTime createdDateTime;

  @LastModifiedDate
  private LocalDateTime updatedDateTime;
}

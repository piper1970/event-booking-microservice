package piper1970.eventservice.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import piper1970.eventservice.common.events.status.EventStatus;

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

  @Version
  @EqualsAndHashCode.Exclude
  private Integer version;

  private String facilitator;
  private String title;
  private String description;
  private String location;
  private LocalDateTime eventDateTime;
  private Integer durationInMinutes;
  private Integer availableBookings;
  private EventStatus eventStatus;
}

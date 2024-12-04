package piper1970.eventservice.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Event {
  @Id
  private Integer id;
  private String title;
  private String description;
  private String location;
  private LocalDateTime eventDateTime;
}

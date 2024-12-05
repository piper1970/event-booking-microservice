package piper1970.bookingservice.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.springframework.data.annotation.Id;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@With
public class Booking {

  @Id
  private Integer id;
  private String event;
  private String username;
  private LocalDateTime eventDateTime;
  private boolean confirmed;
}

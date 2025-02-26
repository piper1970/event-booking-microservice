package piper1970.bookingservice.dto.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class BookingDto {
  private Integer id;
  private Integer eventId;
  private String username;
  private LocalDateTime eventDateTime;
  private String bookingStatus;

}

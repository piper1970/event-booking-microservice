package piper1970.bookingservice.domain;

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

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@With
@Table(name = "bookings", schema = "event_service")
public class Booking {

  @Id
  @EqualsAndHashCode.Exclude
  private Integer id;

  @Version
  @EqualsAndHashCode.Exclude
  private Integer version;

  private Integer eventId;
  private String username;
  private String email;
  private BookingStatus bookingStatus;

}

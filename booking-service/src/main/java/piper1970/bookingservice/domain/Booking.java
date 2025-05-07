package piper1970.bookingservice.domain;

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
  private Integer version;

  private Integer eventId;
  private String username;
  private String email;
//  private LocalDateTime eventDateTime;
  private BookingStatus bookingStatus;

  @CreatedDate
  private LocalDateTime createdDateTime;

  @LastModifiedDate
  private LocalDateTime updatedDateTime;

}

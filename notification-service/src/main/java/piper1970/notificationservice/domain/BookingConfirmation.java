package piper1970.notificationservice.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
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
@ToString
@With
@Table(name="booking_confirmations", schema = "event_service")
public class BookingConfirmation {

  @Id
  private Integer id;

  @Version // TODO: find out how R2DBC handles version mismatch??? what errors to expect???
  private Integer version;

  private Integer bookingId;
  private Integer eventId;
  private UUID confirmationString;
  private String bookingUser;
  private String bookingEmail;
  private LocalDateTime confirmationDateTime; // TODO: is this needed, if createdDateTime works???
  private Integer durationInMinutes;

  private ConfirmationStatus confirmationStatus;

  @CreatedDate
  private LocalDateTime createdDateTime;

  @LastModifiedDate
  private LocalDateTime updatedDateTime;

}

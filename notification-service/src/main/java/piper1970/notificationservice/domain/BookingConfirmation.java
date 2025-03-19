package piper1970.notificationservice.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@With
@Table(name="booking_confirmations", schema = "event_service")
public class BookingConfirmation {

  @Id
  private Integer id;

  private Integer bookingId;
  private Integer eventId;
  private UUID confirmationString;
  private LocalDateTime confirmationDateTime;
  private Duration confirmationDuration;

  private ConfirmationStatus confirmationStatus;

}

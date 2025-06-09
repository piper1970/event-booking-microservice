package piper1970.bookingservice.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Booking for Event")
public class BookingDto {

  @Schema(
      description = "Booking ID"
  )
  private Integer id;

  @Schema(
      description = "Event ID"
  )
  private Integer eventId;

  @Schema(
      description = "User associated with booking"
  )
  private String username;

  @Schema(
      description = "Email of user"
  )
  private String email;

  @Schema(
      description = "status of booking",
      allowableValues = {"IN_PROGRESS", "CONFIRMED", "CANCELLED", "COMPLETED"}
  )
  private String bookingStatus;
}

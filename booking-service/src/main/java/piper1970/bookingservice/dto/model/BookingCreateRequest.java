package piper1970.bookingservice.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request Body for creating a booking")
public class BookingCreateRequest{

  @Schema(
      description = "id of event to book"
  )
  @Positive
  @NotNull
  private Integer eventId;

  @Schema(
      description = "current user's username. supplied through security token",
      accessMode = Schema.AccessMode.READ_ONLY
  )
  @Null(message = "[username] field cannot be set manually. It is set via authentication token")
  private String username;

  @Schema(
      description = "current user's email. supplied through security token",
      accessMode = Schema.AccessMode.READ_ONLY
  )
  @Null(message = "[email] field cannot be set manually. It is set via authentication token")
  private String email;

}

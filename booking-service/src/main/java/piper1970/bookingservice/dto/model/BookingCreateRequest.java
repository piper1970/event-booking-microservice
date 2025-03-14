package piper1970.bookingservice.dto.model;

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
public class BookingCreateRequest{

  @Positive
  @NotNull
  private Integer eventId;

  @Null(message = "[username] field cannot be set manually. It is set via authentication token")
  private String username;

  @Null(message = "[email] field cannot be set manually. It is set via authentication token")
  private String email;
}

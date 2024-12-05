package piper1970.bookingservice.dto.model;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

  @NotBlank(message = "[event] field cannot be blank")
  @Size(max = 255, message = "[event] field must be within 255 character range")
  private String event;

  @NotBlank(message = "[username] field cannot be blank")
  @Size(max = 255, message = "[username] field must be within 255 character range")
  private String username;

  @NotNull(message = "[eventDateTime] field must be present and non-null")
  @FutureOrPresent(message= "[eventDateTime] field cannot be in the past")
  private LocalDateTime eventDateTime;

  private boolean confirmed;
}

package piper1970.eventservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import piper1970.eventservice.common.validation.annotations.CustomFuture;
import piper1970.eventservice.common.validation.annotations.NullOrNotBlank;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@With
public class EventCreateRequest {

  @Null(message = "[facilitator] cannot be set within JSON. It is injected from authentication token")
  private String facilitator;

  @NotBlank(message = "[title] field cannot be empty")
  @Size(max = 255, message = "[title] field must be within 255 character range")
  private String title;

  @NullOrNotBlank(message = "[description] field cannot be blank, but may be omitted")
  @Size(max = 255, message = "[description] field must be within 255 character range")
  private String description;

  @NotBlank(message = "[location] field cannot be empty")
  @Size(max = 255, message = "[location] field must be within 255 character range")
  private String location;

  @NotNull(message = "[eventDateTime] field must be present and non-null")
  @CustomFuture(message = "[eventDateTime] field must be a future date")
  private LocalDateTime eventDateTime;

  @NotNull(message = "[durationInMinutes] field must be present and non-null")
  @Min(value = 30, message = "[durationInMinutes] field must be at least 30 minutes")
  private Integer durationInMinutes;

  @NotNull(message = "[cost] field must be present and non-null")
  @DecimalMin(value = "0.0") // positive-or-zero
  @Digits(integer = 4, fraction = 2) // numeric(6,2)
  private BigDecimal cost;

  @NotNull(message = "[availableBookings] field must be present and non-null")
  @PositiveOrZero(message = "[availableBookings] field cannot be negative")
  @Max(value=32767) // smallint
  private Integer availableBookings;

}

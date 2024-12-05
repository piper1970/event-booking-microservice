package piper1970.eventservice.dto.model;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import piper1970.eventservice.domain.EventStatus;
import piper1970.eventservice.dto.validation.annotations.EnumValues;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class EventDto {
  private Integer id;

  @NotBlank(message = "[title] field cannot be empty")
  @Size(max = 255, message = "[title] field must be within 255 character range")
  private String title;

  @Size(max = 255, message = "[description] field must be within 255 character range")
  private String description;

  @NotBlank(message = "[location] field cannot be empty")
  @Size(max = 255, message = "[location] field must be within 255 character range")
  private String location;

  @NotNull(message = "[eventDateTime] field must be present and non-null")
  @FutureOrPresent(message= "[eventDateTime] field cannot be in the past")
  private LocalDateTime eventDateTime;

  @NotNull(message = "[cost] field must be present and non-null")
  @PositiveOrZero(message = "[cost] field cannot be negative")
  private BigDecimal cost;


  @NotNull(message = "[availableBookings] field must be present and non-null")
  @PositiveOrZero(message = "[availableBookings] field cannot be negative")
  private Integer availableBookings;


  @NotBlank(message = "[eventStatus] field cannot be empty")
  @EnumValues(enumClass = EventStatus.class, message = "[eventStatus] field must be either 'AWAITING', 'IN_PROGRESS', or 'COMPLETED'")
  private String eventStatus;

}

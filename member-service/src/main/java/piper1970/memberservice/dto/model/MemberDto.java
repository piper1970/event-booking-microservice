package piper1970.memberservice.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemberDto {
  private Integer id;

  @NotBlank(message = "[username] field cannot be blank or null")
  @Size(max = 255, message = "[username] field must be no greater than 255 characters")
  private String username;

  // allow sending password from REST, but not displaying in response...
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @NotBlank(message = "[password] field cannot be blank or null")
  @Size(max = 255, message = "[password] field must be no greater than 255 characters")
  private String password;

  @NotBlank(message = "[firstName] field cannot be blank or null")
  @Size(max = 255, message = "[firstName] field must be no greater than 255 characters")
  private String firstName;

  @NotBlank(message = "[lastName] field cannot be blank or null")
  @Size(max = 255, message = "[lastName] field must be no greater than 255 characters")
  private String lastName;

  @NotBlank(message = "[email] field cannot be blank or null")
  @Size(max = 255, message = "[email] field must be no greater than 255 characters")
  @Email(message = "[email] field must contain a valid email")
  private String email;

}

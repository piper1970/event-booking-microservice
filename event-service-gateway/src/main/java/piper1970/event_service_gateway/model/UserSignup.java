package piper1970.event_service_gateway.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserSignup {

  @NotBlank(message = "[username] field cannot be blank or null")
  @Size(max = 255, message = "[username] field must be no greater than 255 characters")
  String username;

  @NotBlank(message = "[password] field cannot be blank or null")
  @Size(max = 255, message = "[password] field must be no greater than 255 characters")
  String password;

  @NotBlank(message = "[confirmPassword] field cannot be blank or null")
  @Size(max = 255, message = "[confirmPassword] field must be no greater than 255 characters")
  String confirmPassword;

  @NotBlank(message = "[firstName] field cannot be blank or null")
  @Size(max = 255, message = "[firstName] field must be no greater than 255 characters")
  String firstName;

  @NotBlank(message = "[lastName] field cannot be blank or null")
  @Size(max = 255, message = "[lastName] field must be no greater than 255 characters")
  String lastName;

  @NotBlank(message = "[email] field cannot be blank or null")
  @Size(max = 255, message = "[email] field must be no greater than 255 characters")
  @Email(message = "[email] field must contain a valid email")
  String email;

}

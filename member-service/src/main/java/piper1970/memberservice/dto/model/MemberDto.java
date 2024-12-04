package piper1970.memberservice.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true, value = "password")
public class MemberDto {
  private Integer id;
  private String username;
  private String password;
  private String firstName;
  private String lastName;
  private String email;
}

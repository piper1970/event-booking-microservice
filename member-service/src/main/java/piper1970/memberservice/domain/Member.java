package piper1970.memberservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Member {

  @Id
  private Integer id;

  private String username;
  private String password;
  private String firstName;
  private String lastName;
  private String email;
}

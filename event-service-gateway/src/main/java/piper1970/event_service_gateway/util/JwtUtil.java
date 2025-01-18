package piper1970.event_service_gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtUtil {

  private final KeyPair keyPair;

  public final Integer expirationInHours;

  private final String issuer;

  public JwtUtil(
      @Value("${jwt.expiration}") Integer expirationInHours,
      @Value("${jwt.issuer}") String issuer
  ) {

    keyPair = Jwts. SIG.RS256.keyPair().build();
    this.expirationInHours = expirationInHours;
    this.issuer = issuer;
  }

  public boolean validateToken(String token, UserDetails userDetails) {
    String username = extractUsername(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
  }

  public String generateToken(Map<String, Object> claims, UserDetails userDetails) {
    claims.put("sub", userDetails.getUsername());
    return createToken(claims, userDetails.getUsername());
  }

  public String generateToken(UserDetails userDetails) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("authorities", userDetails.getAuthorities());
    return generateToken(claims, userDetails);
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
    final Claims claims = extractAllClaims(token);
    return claimResolver.apply(claims);
  }

  public Claims extractAllClaims(String token) {
    return Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token).getPayload();
  }


  public Date extractExpirationDate(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private boolean isTokenExpired(String token) {
    return extractExpirationDate(token).before(new Date());
  }

  private String createToken(Map<String, Object> claims, String username) {
    var now = Instant.now();

    return Jwts.builder()
        .claims()
        .add(claims)
        .subject(username)
        .issuer(issuer)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationInHours, ChronoUnit.HOURS)))
        .and()
        .signWith(keyPair.getPrivate())
        .compact();
  }
}

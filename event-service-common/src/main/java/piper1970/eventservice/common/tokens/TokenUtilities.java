package piper1970.eventservice.common.tokens;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Commonly used token extraction and verification methods used by both Event-Service and Booking-Service
 */
public class TokenUtilities {

  public static String getUserFromToken(Jwt token){
    return token.getClaimAsString("preferred_username");
  }

  public static String getEmailFromToken(Jwt token){
    return token.getClaimAsString("email");
  }

  public static boolean isAdmin(JwtAuthenticationToken token){
    return token.getAuthorities().stream()
        .anyMatch(auth -> "ADMIN".equals(auth.getAuthority()));
  }
}

package piper1970.eventservice.common.tokens;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Commonly used token extraction and verification methods used by both Event-Service and Booking-Service.
 */
public class TokenUtilities {

  /**
   * Extract username from token
   * @param token authentication token
   * @return user | null
   */
  public static @Nullable String getUserFromToken(@NonNull JwtAuthenticationToken token){
    var creds = (Jwt)token.getCredentials();
    return creds.getClaimAsString("preferred_username");
  }

  /**
   * Extract email from token
   * @param token authentication token
   * @return email | null
   */
  public static @Nullable String getEmailFromToken(@NonNull JwtAuthenticationToken token){
    var creds = (Jwt)token.getCredentials();
    return creds.getClaimAsString("email");
  }

  /**
   * Determine if user is an admin
   * @param token authentication token
   * @return true if user is admin, false otherwise
   */
  public static boolean isAdmin(@NonNull JwtAuthenticationToken token){
    return token.getAuthorities().stream()
        .anyMatch(auth -> "ADMIN".equals(auth.getAuthority()));
  }
}

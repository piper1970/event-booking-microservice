package piper1970.bookingservice.repository;

import java.util.Collection;

/**
 * Needed by BookingRepository for
 * {@link BookingRepository#findByEventIdAndBookingStatusNotIn(Integer, Collection)} summary response.
 */
public interface BookingSummary {
  Integer getId();
  Integer getEventId();
  String getUsername();
  String getEmail();
}

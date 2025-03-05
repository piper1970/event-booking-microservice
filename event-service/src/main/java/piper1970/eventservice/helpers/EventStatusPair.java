package piper1970.eventservice.helpers;

import piper1970.eventservice.common.events.status.EventStatus;

public record EventStatusPair(EventStatus before, EventStatus after) {
  public static EventStatusPair of(EventStatus before, EventStatus after) {
    return new EventStatusPair(before, after);
  }
}

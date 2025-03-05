package piper1970.eventservice.helpers;

import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.exceptions.EventUpdateException;

@FunctionalInterface
public interface EventStatusTransitionValidator {
  void validate(EventStatus before, EventStatus after) throws EventUpdateException;
}

package piper1970.eventservice.helpers;

import java.util.Map;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.exceptions.EventUpdateException;


public final class EventStatusTransitionValidators {

  private static final EventStatusTransitionValidator SUCCESS = (first, second) -> {
    // do nothing.. successful validation
  };

  private static final EventStatusTransitionValidator FAILED = (first, second) -> {
    throw new EventUpdateException(
        "Transition from %s to %s is not allowed".formatted(first.name(), second.name()));
  };

  public static final Map<EventStatusPair, EventStatusTransitionValidator> VALIDATION_MAP =
      Map.of(EventStatusPair.of(EventStatus.AWAITING, EventStatus.AWAITING), SUCCESS,
          EventStatusPair.of(EventStatus.AWAITING, EventStatus.IN_PROGRESS), SUCCESS,
          EventStatusPair.of(EventStatus.AWAITING, EventStatus.COMPLETED), FAILED,
          EventStatusPair.of(EventStatus.IN_PROGRESS, EventStatus.IN_PROGRESS),
          SUCCESS,
          EventStatusPair.of(EventStatus.IN_PROGRESS, EventStatus.COMPLETED), SUCCESS,
          EventStatusPair.of(EventStatus.IN_PROGRESS, EventStatus.AWAITING), FAILED,
          EventStatusPair.of(EventStatus.COMPLETED, EventStatus.COMPLETED), SUCCESS,
          EventStatusPair.of(EventStatus.COMPLETED, EventStatus.IN_PROGRESS), FAILED,
          EventStatusPair.of(EventStatus.COMPLETED, EventStatus.AWAITING), FAILED
      );
}

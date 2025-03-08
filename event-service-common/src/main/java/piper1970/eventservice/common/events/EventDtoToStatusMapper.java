package piper1970.eventservice.common.events;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Function;
import org.springframework.lang.Nullable;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;

/**
 * Handler to determine status of event(dto) based on event date-time and duration
 *
 */
public class EventDtoToStatusMapper implements Function<EventDto, EventStatus> {

  private final Clock clock;

  public EventDtoToStatusMapper(Clock clock) {
    this.clock = clock;
  }

  @Override
  public @Nullable EventStatus apply(@Nullable EventDto event) {

    if (event == null) {
      return null;
    }

    var eventDateTime = event.getEventDateTime();

    if (eventDateTime == null) {
      return null;
    }
    var now = LocalDateTime.now(clock);
    if (now.isBefore(eventDateTime)) {
      return EventStatus.AWAITING;
    } else if (now.isAfter(eventDateTime.plusMinutes(event.getDurationInMinutes()))) {
      return EventStatus.COMPLETED;
    }
    return EventStatus.IN_PROGRESS;
  }
}

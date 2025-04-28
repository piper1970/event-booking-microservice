package piper1970.eventservice.service;

import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import reactor.core.publisher.Mono;

public interface MessagePostingService {
  Mono<Void> postEventCancelledMessage(EventCancelled message);
  Mono<Void> postEventChangedMessage(EventChanged message);
  Mono<Void> postEventCompletedMessage(EventCompleted message);
}

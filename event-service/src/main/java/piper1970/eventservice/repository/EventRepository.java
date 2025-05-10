package piper1970.eventservice.repository;

import java.util.Collection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface EventRepository extends ReactiveCrudRepository<Event, Integer> {
  Mono<Event> findByIdAndFacilitator(Integer id, String facilitator);

  Flux<Event> findByEventStatusIn(Collection<EventStatus> eventStatus);
}

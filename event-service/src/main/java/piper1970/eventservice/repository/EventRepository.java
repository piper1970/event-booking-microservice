package piper1970.eventservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.eventservice.domain.Event;
import reactor.core.publisher.Mono;

@Repository
public interface EventRepository extends ReactiveCrudRepository<Event, Integer> {
  Mono<Event> findByIdAndFacilitator(Integer id, String facilitator);
}

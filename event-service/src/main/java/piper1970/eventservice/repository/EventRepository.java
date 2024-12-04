package piper1970.eventservice.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import piper1970.eventservice.domain.Event;

@Repository
public interface EventRepository extends ReactiveCrudRepository<Event, Integer> {
}

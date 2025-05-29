package piper1970.eventservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@Slf4j
public class SchedulingService {

  private final EventRepository eventRepository;
  private final MessagePostingService messagePostingService;
  private final TransactionalOperator transactionalOperator;
  private final Clock clock;
  private final long maxRetries;

  public SchedulingService(EventRepository eventRepository,
      MessagePostingService messagePostingService, TransactionalOperator transactionalOperator,
      Clock clock,
      @Value("${scheduler.retry.max:2}") long maxRetries) {
    this.eventRepository = eventRepository;
    this.messagePostingService = messagePostingService;
    this.transactionalOperator = transactionalOperator;
    this.clock = clock;
    this.maxRetries = maxRetries;
  }

  /**
   * Periodically checks events that are either waiting or in progress to determine if they
   * have completed.
   * Updated repository and sends out EVENT_COMPLETED message to kafka for any records found
   */
  @Scheduled(fixedDelayString = "${scheduler.completed-events.fixed.delay.millis:300000}",
  initialDelayString = "${scheduler.completed-events.initial.delay.millis:60000}")
  @SchedulerLock(name="checkForCompletedEvents", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void checkForCompletedEvents(){
    // 1. Find all events with status either AWAITING or IN_PROGRESS
    // 2. Extract all events that have completed
    // 3. Save all completed events to repo
    // 4. Send out EVENT_COMPLETED kafka message

    log.info("Checking for completed events");

    try{
      eventRepository.findByEventStatusIn(List.of(EventStatus.AWAITING, EventStatus.IN_PROGRESS))
          .subscribeOn(Schedulers.boundedElastic())
          .filter(this::isCompleted)
          .flatMap(event ->
              eventRepository.save(event.withEventStatus(EventStatus.COMPLETED))
                  .subscribeOn(Schedulers.boundedElastic())
          )
          // add transaction behavior for optimistic locking via version field
          .as(transactionalOperator::transactional)
          // allow for retries in case of optimistic lock failures between fetching and saving
          .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500L))
              .filter(throwable -> throwable instanceof OptimisticLockingFailureException)
              .jitter(0.7D))
          .flatMap(this::postCompletedEventToKakfa)
          .count()
          .doOnNext(count -> log.info("{} completed events have been processed", count))
          // IMPORTANT: make sure blocked duration is less thant shedlock lockAtLeastFor duration
          .block(Duration.ofMinutes(4));

    }catch(RuntimeException e){
      log.error("Unable to finish scheduled 'checkForCompletedEvents' job", e);
    }
  }

  /**
   * Periodically checks events that are in progress to determine if they
   * have started.
   * Updates repository for any records found
   */
  @Scheduled(fixedDelayString = "${scheduler.started-events.fixed.delay.millis:300000}",
      initialDelayString = "${scheduler.started-events.initial.delay.millis:60000}")
  @SchedulerLock(name="checkForAwaitingEventsThatHaveStarted", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void checkForAwaitingEventsThatHaveStarted(){
    // 1. Find all events in the AWAITING status
    // 2. Extract all events based on whether they have started (but not completed)
    // 3. Save events with status updated to IN_PROGRESS

    log.info("Checking for awaiting events that have started");

    try{
      eventRepository.findByEventStatusIn(List.of(EventStatus.AWAITING))
          .subscribeOn(Schedulers.boundedElastic())
          .filter(this::isInProgress)
          .flatMap(event -> eventRepository.save(event.withEventStatus(EventStatus.IN_PROGRESS))
              .subscribeOn(Schedulers.boundedElastic())
          )
          // add transaction behavior for optimistic locking via version field
          .as(transactionalOperator::transactional)
          // allow for retries in case of optimistic lock failures between fetching and saving
          .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500L))
              .filter(throwable -> throwable instanceof OptimisticLockingFailureException)
              .jitter(0.7D))
          .count()
          .doOnNext(count -> log.info("{} events have been updated to IN_PROGRESS", count))
          // IMPORTANT: make sure blocked duration is less thant shedlock lockAtLeastFor duration
          .block(Duration.ofMinutes(4));
    }catch(RuntimeException e){
      log.error("Unable to finish scheduled 'checkForAwaitingEventsThatHaveStarted' job", e);
    }
  }

  private Mono<Integer> postCompletedEventToKakfa(Event event){
    EventCompleted message = new EventCompleted();
    message.setEventId(event.getId());
    return messagePostingService.postEventCompletedMessage(message)
        .subscribeOn(Schedulers.boundedElastic())
        // send back single value so count() can be properly used downstream
        .then(Mono.just(1));
  }

  /**
   * Determines whether event is in progress, based on current time, event-start-time and duration
   */
  private boolean isInProgress(Event event){
    var now = LocalDateTime.now(clock);
    var startTime = event.getEventDateTime();
    var endTime = startTime.plusMinutes(event.getDurationInMinutes());
    return now.isAfter(startTime) && now.isBefore(endTime);
  }

  /**
   * Determines whether event has completed, based on current time, event-start-time and duration
   */
  private boolean isCompleted(Event event){
    var now = LocalDateTime.now(clock);
    var startTime = event.getEventDateTime();
    var endTime = startTime.plusMinutes(event.getDurationInMinutes());
    return now.isAfter(endTime);
  }

}

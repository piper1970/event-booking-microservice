package piper1970.notificationservice.service;

import io.micrometer.core.instrument.Counter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Service for handling periodic cleanup of booking-confirmation repository.
 */
@Service
@Slf4j
public class SchedulingService {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MessagePostingService messagePostingService;
  private final TransactionalOperator transactionalOperator;
  private final Counter expirationCounter;
  private final Clock clock;

  /**
   * Number of hours until confirmation record is considered stale.
   */
  private final long staleDataDurationInHours;

  private final long maxRetries;

  public SchedulingService(BookingConfirmationRepository bookingConfirmationRepository,
      MessagePostingService messagePostingService, TransactionalOperator transactionalOperator,
      @Qualifier("expirations") Counter expirationCounter,
      Clock clock,
      @Value("${scheduler.stale.data.duration.hours:6}") long staleDataDurationInHours,
      @Value("${scheduler.expired.confirmations.max.retries:10}") long maxRetries) {
    this.bookingConfirmationRepository = bookingConfirmationRepository;
    this.messagePostingService = messagePostingService;
    this.transactionalOperator = transactionalOperator;
    this.clock = clock;
    this.staleDataDurationInHours = staleDataDurationInHours;
    this.maxRetries = maxRetries;
    this.expirationCounter = expirationCounter;
  }

  /**
   * Periodically checks for non-confirmed records that have expired, updating repository and posting to kafka if any are found
   */
  @Scheduled(fixedDelayString = "${scheduler.expired.confirmations.fixed.delay.millis:900000}", initialDelayString = "${scheduler.expired.confirmations.initial.delay.millis:60000}")
  @SchedulerLock(name = "checkForExpiredConfirmationsLock", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void checkForExpiredConfirmations(){

    log.info("Checking for expired confirmations");

    // 1. find all confirmations that have passed the confirmation window
    // 2. update confirmation states to 'expired'.
    // 3. post kafka message for each booking that has expired

    try{
      bookingConfirmationRepository.findByConfirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
          .subscribeOn(Schedulers.boundedElastic())
          // check to see if the expiration window has past
          .filter(this::filterForExpiredConfirmations)
          // update record in repo with expired status
          .flatMap(confirmation ->
              bookingConfirmationRepository.save(confirmation.withConfirmationStatus(
                      ConfirmationStatus.EXPIRED))
                  .subscribeOn(Schedulers.boundedElastic())
          )
          // add transaction behavior for optimistic locking via version field
          .as(transactionalOperator::transactional)
          // allow for retries in case of optimistic lock failures between fetching and saving
          .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500L))
              .filter(throwable -> throwable instanceof OptimisticLockingFailureException)
              .jitter(0.7D))
          // post booking-expired message to kafka
          .flatMap(this::postExpiredConfirmationMessageToKafka)
          .count()
          .doOnNext(count -> {
            log.info("{} expired confirmations have been processed", count);
            expirationCounter.increment(count);
          })
          // IMPORTANT: make sure blocked duration is less thant shedlock lockAtLeastFor duration
          .block(Duration.ofMinutes(4));
    }catch(RuntimeException e){
      log.error("Unable to finish scheduled checkForExpiredConfirmations job", e);
    }
  }



  /**
   * Periodically deletes confirmations that have been in the system for over a given number of hours
   *
   * @see #staleDataDurationInHours
   */
  @Scheduled(fixedDelayString = "${scheduler.stale.data.fixed.delay.millis:900000}", initialDelayString = "${scheduler.stale.data.initial.delay.millis:60000}")
  @SchedulerLock(name = "clearStaleDataLock", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void clearStaleData(){

    log.info("Clearing stale data");
    var deletionDateTime = LocalDateTime.now(clock).minusHours(staleDataDurationInHours);

    try{
      bookingConfirmationRepository.deleteByConfirmationDateTimeBefore(deletionDateTime)
          .subscribeOn(Schedulers.boundedElastic())
          .doOnNext(deleteCount -> log.info("Deleted [{}] confirmation records that were older than {}:", deleteCount, deletionDateTime))
          // IMPORTANT: make sure blocked duration is less thant shedlock lockAtLeastFor duration
          .block(Duration.ofMinutes(4));
    }catch(RuntimeException e){
      log.error("Unable to finish scheduled clearStaleData job", e);
    }
  }

  /**
   * Posts BookingExpired message to kafka topic based off expiredConfirmation parameter.
   */
  private Mono<Integer> postExpiredConfirmationMessageToKafka(BookingConfirmation expiredConfirmation) {
    BookingExpired bookingExpiredMessage = new BookingExpired();
    bookingExpiredMessage.setEventId(expiredConfirmation.getEventId());
    BookingId booking = new BookingId();
    booking.setId(expiredConfirmation.getBookingId());
    booking.setEmail(expiredConfirmation.getBookingEmail());
    booking.setUsername(expiredConfirmation.getBookingUser());
    bookingExpiredMessage.setBooking(booking);
    return messagePostingService.postBookingExpiredMessage(bookingExpiredMessage)
        .then(Mono.just(1)); // for later count of posted messages
  }

  /**
   * Filters out confirmations that are not expired.
   * Defines expiration as 'confirmationDateTime + durationInMinutes' time already in past
   * Assumption: bookingConfirmation parameter is currently in the AWAITING_CONFIRMATION status.
   */
  private boolean filterForExpiredConfirmations(BookingConfirmation bookingConfirmation) {
    var currentTime = LocalDateTime.now(clock);
    var expirationTime = bookingConfirmation.getConfirmationDateTime().plusMinutes(bookingConfirmation.getDurationInMinutes());
    return currentTime.isAfter(expirationTime);
  }
}

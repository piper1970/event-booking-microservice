package piper1970.notificationservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MessagePostingService messagePostingService;
  private final TransactionalOperator transactionalOperator;
  private final Clock clock;

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
          .filter(this::filterForExpiredConfirmations)
          .flatMap(confirmation ->
              bookingConfirmationRepository.save(confirmation.withConfirmationStatus(ConfirmationStatus.EXPIRED))
                  .subscribeOn(Schedulers.boundedElastic())
          )
          .as(transactionalOperator::transactional)
          .retryWhen(Retry.max(10)
              .filter(throwable -> throwable instanceof OptimisticLockingFailureException))
          .flatMap(this::postExpiredConfirmationMessageToKafka)
          .then()
          // make sure blocked duration is less thant shedlock lockAtLeastFor duration
          .block(Duration.ofMinutes(4));
    }catch(RuntimeException e){
      log.error("Unable to finish scheduled checkForExpiredConfirmations job", e);
    }
  }



  /**
   * Periodically deletes confirmations that have been in the system for over 6 hours
   */
  @Scheduled(fixedDelayString = "${scheduler.stale.data.fixed.delay.millis:900000}", initialDelayString = "${scheduler.stale.data.initial.delay.millis:60000}")
  @SchedulerLock(name = "clearStaleDataLock", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void clearStaleData(){

    log.info("Clearing stale data");
    var deletionDateTime = LocalDateTime.now(clock).minusHours(6); //TODO: make this a property

    try{
      bookingConfirmationRepository.deleteByConfirmationDateTimeBefore(deletionDateTime)
          .subscribeOn(Schedulers.boundedElastic())
          .doOnNext(deleteCount -> log.info("Deleted [{}] confirmation records that were older than {}:", deleteCount, deletionDateTime))
          // make sure blocked duration is less thant shedlock lockAtLeastFor duration
          .block(Duration.ofMinutes(4));
    }catch(RuntimeException e){
      log.error("Unable to finish scheduled clearStaleData job", e);
    }
  }

  /**
   * Posts BookingExpired message to kafka topic based off expiredConfirmation parameter.
   */
  private Mono<Void> postExpiredConfirmationMessageToKafka(BookingConfirmation expiredConfirmation) {
    BookingExpired bookingExpiredMessage = new BookingExpired();
    bookingExpiredMessage.setEventId(expiredConfirmation.getEventId());
    BookingId booking = new BookingId();
    booking.setId(expiredConfirmation.getBookingId());
    booking.setEmail(expiredConfirmation.getBookingEmail());
    booking.setUsername(expiredConfirmation.getBookingUser());
    bookingExpiredMessage.setBooking(booking);
    return messagePostingService.postBookingExpiredMessage(bookingExpiredMessage)
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Filters out confirmations that are not expired.
   * Defines expiration as 'confirmationDateTime + durationInMinutes' time already in past
   * Expectation: bookingConfirmation parameter is currently in the AWAITING_CONFIRMATION status.
   */
  private boolean filterForExpiredConfirmations(BookingConfirmation bookingConfirmation) {
    var currentTime = LocalDateTime.now(clock);
    var expirationTime = bookingConfirmation.getConfirmationDateTime().plusMinutes(bookingConfirmation.getDurationInMinutes());
    return currentTime.isAfter(expirationTime);
  }
}

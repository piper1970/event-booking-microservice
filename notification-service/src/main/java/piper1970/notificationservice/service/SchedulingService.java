package piper1970.notificationservice.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MessagePostingService messagePostingService;
  private final Clock clock;

  /// Schedules are run using a single-threaded task-executor, ensuring no corruption due to overlap.

  // defaults to 15 minute for fixedDelayString
  // defaults to 1  minute for initialDelayString
  @Scheduled(fixedDelayString = "${scheduler.fixed.delay:900000}", initialDelayString = "${scheduler.initial.delay:60000}")
  @SchedulerLock(name = "checkForExpiredConfirmationsLock", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void checkForExpiredConfirmations(){

    log.info("Checking for expired confirmations");

    // TODO: add timeout, scheduler, and retry logic
    bookingConfirmationRepository.findByConfirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
        .filter(this::filterForExpiredConfirmations)
        .flatMap(confirmation ->
            bookingConfirmationRepository.save(confirmation.withConfirmationStatus(ConfirmationStatus.EXPIRED)))
        .flatMap(this::postExpiredConfirmationMessageToKafka)
        .then()
        .block();
  }

  // defaults to 15 minute for fixedDelayString
  // defaults to 1  minute for initialDelayString
  @Scheduled(fixedDelayString = "${scheduler.fixed.delay:900000}", initialDelayString = "${scheduler.initial.delay:60000}")
  @SchedulerLock(name = "clearStaleDataLock", lockAtLeastFor = "${shedlock.lockAtLeastFor.default:PT5M}")
  public void clearStaleData(){
    log.info("Clearing stale data");
    // TODO: need to add logic here...
  }

  /**
   * Takes expired confirmation message and posts to kafka topic.
   *
   * @param expiredConfirmation BookingConfirmation with expired status
   * @return Mono[Void]
   */
  private Mono<Void> postExpiredConfirmationMessageToKafka(BookingConfirmation expiredConfirmation) {
    BookingExpired bookingExpiredMessage = new BookingExpired();
    bookingExpiredMessage.setEventId(expiredConfirmation.getEventId());
    BookingId booking = new BookingId();
    booking.setId(expiredConfirmation.getBookingId());
    booking.setEmail(expiredConfirmation.getBookingEmail());
    booking.setUsername(expiredConfirmation.getBookingUser());
    bookingExpiredMessage.setBooking(booking);
    return messagePostingService.postBookingExpiredMessage(bookingExpiredMessage);
  }

  /**
   * Filter out confirmations that are not expired.
   * Expects parameter to be in the AWAITING_CONFIRMATION status.
   * Checks to see whether combination of confirmation-time and confirmation duration has already passed.
   *
   * @param bookingConfirmation BookingConfirmation
   * @return true if confirmation window has passed, false otherwise
   */
  private boolean filterForExpiredConfirmations(BookingConfirmation bookingConfirmation) {
    var currentTime = LocalDateTime.now(clock);
    var expirationTime = bookingConfirmation.getConfirmationDateTime().plusMinutes(bookingConfirmation.getDurationInMinutes());
    return currentTime.isAfter(expirationTime);
  }
}

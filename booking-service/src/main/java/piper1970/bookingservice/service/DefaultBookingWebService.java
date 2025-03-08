package piper1970.bookingservice.service;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.exceptions.BookingCancellationException;
import piper1970.bookingservice.exceptions.BookingCreationException;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.exceptions.BookingTimeoutException;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DefaultBookingWebService implements BookingWebService {

  private final BookingRepository bookingRepository;
  private final EventRequestService eventRequestService;
  private final EventDtoToStatusMapper eventDtoToStatusMapper;
  private final Long bookingRepositoryTimeoutInMilliseconds;
  private final Duration bookingTimeoutDuration;

  public DefaultBookingWebService(BookingRepository bookingRepository,
      EventRequestService eventRequestService,
      EventDtoToStatusMapper eventDtoToStatusMapper,
      @Value("${booking-repository.timout.milliseconds}") Long bookingRepositoryTimeoutInMilliseconds) {
    this.bookingRepository = bookingRepository;
    this.eventRequestService = eventRequestService;
    this.eventDtoToStatusMapper = eventDtoToStatusMapper;
    this.bookingRepositoryTimeoutInMilliseconds = bookingRepositoryTimeoutInMilliseconds;
    bookingTimeoutDuration = Duration.ofMillis(bookingRepositoryTimeoutInMilliseconds);
  }

  @Override
  public Flux<Booking> findAllBookings() {
    return bookingRepository.findAll()
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding all bookings"), ex)))
        .doOnNext(this::logBookingRetrieval);
  }

  @Override
  public Flux<Booking> findBookingsByUsername(String username) {
    return bookingRepository.findByUsername(username)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding bookings by usernames"), ex)))
        .doOnNext(booking -> logBookingRetrieval(booking, username));
  }

  @Override
  public Mono<Booking> findBookingById(Integer id) {
    return bookingRepository.findById(id)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding booking by id"), ex)))
        .doOnNext(this::logBookingRetrieval);
  }

  @Override
  public Mono<Booking> findBookingByIdAndUsername(Integer id, String username) {
    return bookingRepository.findBookingByIdAndUsername(id, username)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding booking by id and username"), ex)))
        .doOnNext(booking -> logBookingRetrieval(booking, username));
  }

  @Override
  public Mono<Booking> createBooking(BookingCreateRequest createRequest, String token) {

    Predicate<EventDto> validEvent = dto ->
        dto.getAvailableBookings() >= 1
            && EventStatus.AWAITING == eventDtoToStatusMapper.apply(dto);

    // TODO: contact EventService to see if event is available and get the time
    return eventRequestService.requestEvent(createRequest.getEventId(), token)
        .filter(validEvent)
        // throw error if event in question has already started
        .switchIfEmpty(Mono.error(new BookingCreationException(
            "Unable to create booking for event that has already started")))
        // convert to Booking and save to repo
        .flatMap(dto -> {
              var booking = Booking.builder()
                  .eventId(createRequest.getEventId())
                  .username(createRequest.getUsername())
                  .eventDateTime(dto.getEventDateTime())
                  .bookingStatus(BookingStatus.IN_PROGRESS)
                  .build();
              return bookingRepository.save(booking);
            }
        )
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("saving booking"), ex))
        ).doOnNext(booking -> {
          log.debug("Booking [{}] has been created for [{}]", booking.getId(),
              booking.getUsername());
          // TODO: need to send CREATED_BOOKING event
        });
  }

  @Transactional
  @Override
  public Mono<Void> deleteBooking(Integer id, String token) {

    Predicate<EventDto> validEvent = dto -> EventStatus.IN_PROGRESS != eventDtoToStatusMapper.apply(
        dto);

    return bookingRepository.findById(id)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding booking by id"), ex)))
        // throw BookingNotFoundException if booking not in repo
        .switchIfEmpty(Mono.defer(
            () -> Mono.error(new BookingNotFoundException("Booking not found for id: " + id))))
        // make call to event-service - timeout behavior handled in eventRequestService
        .flatMap(booking -> eventRequestService.requestEvent(booking.getEventId(), token))
        // ensure event not in progress. if so, throw BookingCancellationException
        .filter(validEvent)
        .switchIfEmpty(Mono.defer(() -> Mono.error(new BookingCancellationException(
            String.format("Booking [%s] can no longer be cancelled for the event", id)))))
        // finally, delete the booking
        .flatMap(ignored -> bookingRepository.deleteById(id))
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("deleting booking by id"), ex)))
        .doOnSuccess(_void -> {
          log.debug("Booking [{}] has been deleted", id);

          // TODO: Emit CancelBooking event to kafka (event, booker)
        });
  }

  private void logBookingRetrieval(Booking booking) {
    log.debug("Booking [{}] has been retrieved", booking.getEventId());
  }

  private void logBookingRetrieval(Booking booking, String username) {
    log.debug("Booking [{}] has been retrieved for [{}]", booking.getEventId(), username);
  }

  private String provideTimeoutErrorMessage(String subMessage) {
    return String.format("Booking repository timed out [over %d milliseconds] %s",
        bookingRepositoryTimeoutInMilliseconds, subMessage);
  }
}

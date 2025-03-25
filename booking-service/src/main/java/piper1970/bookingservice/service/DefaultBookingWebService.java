package piper1970.bookingservice.service;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.dto.mapper.BookingMapper;
import piper1970.bookingservice.dto.model.BookingCreateRequest;
import piper1970.bookingservice.dto.model.BookingDto;
import piper1970.bookingservice.exceptions.BookingCancellationException;
import piper1970.bookingservice.exceptions.BookingCreationException;
import piper1970.bookingservice.exceptions.BookingNotFoundException;
import piper1970.bookingservice.exceptions.BookingTimeoutException;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.EventNotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DefaultBookingWebService implements BookingWebService {

  private final BookingMapper bookingMapper;
  private final BookingRepository bookingRepository;
  private final EventRequestService eventRequestService;
  private final MessagePostingService messagePostingService;
  private final EventDtoToStatusMapper eventDtoToStatusMapper;
  private final Long bookingRepositoryTimeoutInMilliseconds;
  private final Duration bookingTimeoutDuration;

  public DefaultBookingWebService(
      BookingMapper bookingMapper,
      BookingRepository bookingRepository,
      EventRequestService eventRequestService,
      MessagePostingService messagePostingService,
      EventDtoToStatusMapper eventDtoToStatusMapper,
      @Value("${booking-repository.timout.milliseconds}") Long bookingRepositoryTimeoutInMilliseconds) {
    this.bookingMapper = bookingMapper;
    this.bookingRepository = bookingRepository;
    this.eventRequestService = eventRequestService;
    this.messagePostingService = messagePostingService;
    this.eventDtoToStatusMapper = eventDtoToStatusMapper;
    this.bookingRepositoryTimeoutInMilliseconds = bookingRepositoryTimeoutInMilliseconds;
    bookingTimeoutDuration = Duration.ofMillis(bookingRepositoryTimeoutInMilliseconds);
  }

  @Override
  public Flux<BookingDto> findAllBookings() {
    return bookingRepository.findAll()
        .map(bookingMapper::entityToDto)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding all bookings"), ex)))
        .doOnNext(this::logBookingRetrieval);
  }

  @Override
  public Flux<BookingDto> findBookingsByUsername(String username) {
    return bookingRepository.findByUsername(username)
        .map(bookingMapper::entityToDto)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding bookings by usernames"), ex)))
        .doOnNext(booking -> logBookingRetrieval(booking, username));
  }

  @Override
  public Mono<BookingDto> findBookingById(Integer id) {
    return bookingRepository.findById(id)
        .map(bookingMapper::entityToDto)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding booking by id"), ex)))
        .doOnNext(this::logBookingRetrieval);
  }

  @Override
  public Mono<BookingDto> findBookingByIdAndUsername(Integer id, String username) {
    return bookingRepository.findByIdAndUsername(id, username)
        .map(bookingMapper::entityToDto)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding booking by id and username"), ex)))
        .doOnNext(booking -> logBookingRetrieval(booking, username));
  }

  @Override
  public Mono<BookingDto> createBooking(BookingCreateRequest createRequest, String token) {

    Predicate<EventDto> validEvent = dto ->
        dto.getAvailableBookings() >= 1
            && EventStatus.AWAITING == eventDtoToStatusMapper.apply(dto);

    return eventRequestService.requestEvent(createRequest.getEventId(), token)
        .switchIfEmpty(Mono.error(new EventNotFoundException(createEventNotFountMessage(createRequest.getEventId()))))
        .filter(validEvent)
        // throw error if event in question has already started
        .switchIfEmpty(Mono.error(new BookingCreationException(
            "Unable to create booking for event that has already started")))
        // convert to Booking and save to repo
        .flatMap(dto -> {
              var booking = Booking.builder()
                  .eventId(createRequest.getEventId())
                  .username(createRequest.getUsername())
                  .email(createRequest.getEmail())
                  .eventDateTime(dto.getEventDateTime())
                  .bookingStatus(BookingStatus.IN_PROGRESS)
                  .build();
              return bookingRepository.save(booking);
            }
        )
        .timeout(bookingTimeoutDuration)
        .map(bookingMapper::entityToDto)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("saving booking"), ex))
        ).doOnNext(dto -> {
          log.debug("Booking [{}] has been created for [{}]", dto.getId(),
              dto.getUsername());
          var bookingCreatedMessage = createBookingCreatedMessage(dto);

          // send asynchronously via messaging service
          messagePostingService.postBookingCreatedMessage(bookingCreatedMessage);
        });
  }

  @Override
  public Mono<BookingDto> cancelBooking(Integer id, String username, String token) {
    return bookingRepository.findByIdAndUsername(id, username)
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("finding booking by id"), ex)))
        .switchIfEmpty(Mono.defer(
            () -> {
              log.debug("Booking [{}] has been cancelled", id);
              return Mono.error(new BookingNotFoundException("Booking not found for id: " + id));
            } ))
        .flatMap(booking -> handleCancellationLogic(booking, token));
  }

  /// Handles logic of getting event from event-service, verifying timeframe, and sending appropriate kafka messages
  private Mono<BookingDto> handleCancellationLogic(Booking booking, String token){

    Predicate<EventDto> validEvent = dto -> EventStatus.AWAITING == eventDtoToStatusMapper.apply(
        dto);

    return eventRequestService.requestEvent(booking.getEventId(), token)
        .filter(validEvent)
        .flatMap(event -> {
          log.info("Booking [{}] for event [{}] has been cancelled", booking.getId(), event.getId());
          return bookingRepository.save(booking.withBookingStatus(BookingStatus.CANCELLED));
        })
        .timeout(bookingTimeoutDuration)
        .onErrorResume(TimeoutException.class, ex ->
            Mono.error(new BookingTimeoutException(
                provideTimeoutErrorMessage("saving cancelled booking"), ex)))
        .map(bookingMapper::entityToDto)
        .switchIfEmpty(Mono.defer(() -> {
          log.warn("Cancel of booking [{}] came too late for event [{}]", booking.getId(),
              booking.getEventId());
          return Mono.error(new BookingCancellationException(
              String.format("Booking [%s] can no longer be cancelled for the event", booking.getId())));
        }))
        .doOnSuccess(bookingDto -> {

          log.debug("Booking [{}] has been cancelled", booking.getId());

          var bookingCancelledMessage = createBookingCancelledMessage(bookingDto);
          messagePostingService.postBookingCancelledMessage(bookingCancelledMessage);

        });
  }

  private BookingCreated createBookingCreatedMessage(BookingDto booking) {
    var message = new BookingCreated();
    message.setEventId(booking.getEventId());
    booking.setUsername(booking.getUsername());
    message.setBooking(dtoToBookingId(booking));
    return message;
  }

  private BookingCancelled createBookingCancelledMessage(BookingDto booking) {
    var message = new BookingCancelled();
    message.setEventId(booking.getEventId());
    message.setBooking(dtoToBookingId(booking));
    return message;
  }
  private void logBookingRetrieval(BookingDto booking) {
    log.debug("Booking [{}] has been retrieved", booking.getEventId());
  }

  private void logBookingRetrieval(BookingDto booking, String username) {
    log.debug("Booking [{}] has been retrieved for [{}]", booking.getEventId(), username);
  }

  private String provideTimeoutErrorMessage(String subMessage) {
    return String.format("Booking repository timed out [over %d milliseconds] %s",
        bookingRepositoryTimeoutInMilliseconds, subMessage);
  }

  private String createEventNotFountMessage(Integer eventId) {
    return String.format("Event [%d] not found", eventId);
  }

  private BookingId dtoToBookingId(BookingDto dto) {
    BookingId bookingId = new BookingId();
    bookingId.setId(dto.getId());
    bookingId.setUsername(dto.getUsername());
    bookingId.setEmail(dto.getEmail());
    return bookingId;
  }
}

package piper1970.bookingservice.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
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
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.exceptions.KafkaPostingException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@Slf4j
public class DefaultBookingWebService implements BookingWebService {

  private final BookingMapper bookingMapper;
  private final BookingRepository bookingRepository;
  private final EventRequestService eventRequestService;
  private final MessagePostingService messagePostingService;
  private final TransactionalOperator transactionalOperator;
  private final Long bookingRepositoryTimeoutInMilliseconds;
  private final Duration bookingTimeoutDuration;
  private final Retry defaultRepositoryRetry;
  private final Retry defaultKafkaRetry;

  public DefaultBookingWebService(
      BookingMapper bookingMapper,
      BookingRepository bookingRepository,
      EventRequestService eventRequestService,
      MessagePostingService messagePostingService,
      TransactionalOperator transactionalOperator,
      @Value("${booking-repository.timout.milliseconds}") Long bookingRepositoryTimeoutInMilliseconds,
      @Qualifier("repository") Retry defaultRepositoryRetry,
      @Qualifier("kafka") Retry defaultKafkaRetry) {
    this.bookingMapper = bookingMapper;
    this.bookingRepository = bookingRepository;
    this.eventRequestService = eventRequestService;
    this.messagePostingService = messagePostingService;
    this.transactionalOperator = transactionalOperator;
    this.bookingRepositoryTimeoutInMilliseconds = bookingRepositoryTimeoutInMilliseconds;
    bookingTimeoutDuration = Duration.ofMillis(bookingRepositoryTimeoutInMilliseconds);
    this.defaultRepositoryRetry = defaultRepositoryRetry;
    this.defaultKafkaRetry = defaultKafkaRetry;
  }

  @Override
  public Flux<BookingDto> findAllBookings() {

    return bookingRepository.findAll()
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(bookingTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryFluxTimeout(ex, "finding all bookings"))
        .map(bookingMapper::entityToDto)
        .doOnNext(this::logBookingRetrieval);
  }

  @Override
  public Flux<BookingDto> findBookingsByUsername(String username) {

    return bookingRepository.findByUsername(username)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(bookingTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryFluxTimeout(ex,
            "finding all bookings by username [%s]".formatted(username)))
        .map(bookingMapper::entityToDto)
        .doOnNext(booking -> logBookingRetrieval(booking, username));
  }

  @Override
  public Mono<BookingDto> findBookingById(Integer id) {

    return bookingRepository.findById(id)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(bookingTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(
            ex -> handleRepositoryTimeout(ex, "finding booking for id [%d]".formatted(id)))
        .map(bookingMapper::entityToDto)
        .doOnNext(this::logBookingRetrieval);
  }

  @Override
  public Mono<BookingDto> findBookingByIdAndUsername(Integer id, String username) {

    return bookingRepository.findByIdAndUsername(id, username)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(bookingTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryTimeout(ex,
            "finding booking for id [%d] and username [%s]".formatted(id, username)))
        .switchIfEmpty(Mono.error(new BookingNotFoundException(
            String.format("Booking [%d] not found", id))))
        .map(bookingMapper::entityToDto)
        .doOnNext(booking -> logBookingRetrieval(booking, username));
  }

  @Override
  public Mono<BookingDto> createBooking(BookingCreateRequest createRequest, String token) {

    return eventRequestService.requestEvent(createRequest.getEventId(), token)
        // don't book requests if event is already in progress or completed/cancelled
        .filter(dto ->
            dto.getAvailableBookings() >= 1
                && EventStatus.AWAITING == dto.getEventStatus())
        .switchIfEmpty(Mono.error(new BookingCreationException(
            "Unable to create booking for event that has already started")))
        // save booking from event request to db
        .flatMap(dto -> {
          var eventId = dto.getId();
          var username = createRequest.getUsername();
              var booking = Booking.builder()
                  .eventId(eventId)
                  .username(username)
                  .email(createRequest.getEmail())
                  .bookingStatus(BookingStatus.IN_PROGRESS)
                  .build();
              log.debug("Saving booking for user [{}] at event [{}] to repository", username, eventId);
              return bookingRepository.save(booking)
                  .subscribeOn(Schedulers.boundedElastic())
                  .timeout(bookingTimeoutDuration)
                  .retryWhen(defaultRepositoryRetry)
                  .onErrorResume(ex -> handleRepositoryTimeout(ex, "saving book"));
            }
        )
        // post kafka booking-created message
        .flatMap(booking -> {
          var dto = bookingMapper.entityToDto(booking);
          var bookingCreatedMessage = createBookingCreatedMessage(dto);
          return messagePostingService.postBookingCreatedMessage(bookingCreatedMessage)
              .timeout(bookingTimeoutDuration)
              .retryWhen(defaultKafkaRetry)
              .onErrorResume(ex -> handlePostingTimeout(ex, dto.getId(), "BOOKING_CREATED"))
              .then(Mono.just(dto));
        })
        .as(transactionalOperator::transactional);
  }

  @Override
  public Mono<BookingDto> cancelBooking(Integer id, String username, String token) {

    return bookingRepository.findByIdAndUsername(id, username)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(bookingTimeoutDuration)
        .retryWhen(defaultRepositoryRetry)
        .onErrorResume(ex -> handleRepositoryTimeout(ex,
            "finding book by id [%d] and username [%s]".formatted(id, username)))
        .switchIfEmpty(Mono.fromCallable(
            () -> {
              logBookingCancellation(id);
              throw new BookingNotFoundException("Booking not found for id: " + id);
            }))
        .flatMap(booking -> handleCancellationLogic(booking, token))
        .as(transactionalOperator::transactional);
  }

  /// Handles logic of getting event from event-service, verifying timeframe, and sending
  /// appropriate kafka messages
  private Mono<BookingDto> handleCancellationLogic(Booking booking, String token) {

    // Prevent multiple cancellations of same booking (can update state of the event's availableBookings field)
    if(BookingStatus.CANCELLED == booking.getBookingStatus()) {
      return Mono.error(new BookingCancellationException("Booking has already been cancelled"));
    }

    return eventRequestService.requestEvent(booking.getEventId(), token)
        .filter(dto ->
            EventStatus.AWAITING == dto.getEventStatus())
        .flatMap(event -> {
          log.info("Booking [{}] for event [{}] has been cancelled", booking.getId(),
              event.getId());
          return bookingRepository.save(booking.withBookingStatus(BookingStatus.CANCELLED))
              .subscribeOn(Schedulers.boundedElastic())
              .timeout(bookingTimeoutDuration)
              .retryWhen(defaultRepositoryRetry)
              .onErrorResume(ex -> handleRepositoryTimeout(ex,
                  "saving cancelled book [%d]".formatted(booking.getId())));
        })
        .switchIfEmpty(Mono.fromCallable(() -> {
          log.warn("Cancel of booking [{}] came too late for event [{}]", booking.getId(),
              booking.getEventId());
          throw new BookingCancellationException(
              String.format("Booking [%s] can no longer be cancelled for the event",
                  booking.getId()));
        }))
        .doOnNext(bookingDto -> logBookingCancellation(booking.getId()))
        .flatMap(updatedBooking -> {
              var bookingDto = bookingMapper.entityToDto(updatedBooking);
              var bookingCancelledMessage = createBookingCancelledMessage(bookingDto);
              return messagePostingService.postBookingCancelledMessage(bookingCancelledMessage)
                  .timeout(bookingTimeoutDuration)
                  .retryWhen(defaultKafkaRetry)
                  .onErrorResume(ex -> handlePostingTimeout(ex, bookingDto.getId(), "BOOKING_CANCELLED"))
                  .then(Mono.just(bookingDto));
            }
        );
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

  private void logBookingCancellation(Integer id) {
    log.debug("Booking [{}] has been cancelled", id);
  }

  private Mono<Booking> handleRepositoryTimeout(Throwable ex, String subMessage) {
    if (Exceptions.isRetryExhausted(ex)) {
      return Mono.error(new BookingTimeoutException(
          provideTimeoutErrorMessage(
              "attempting to %s. Exhausted all retries".formatted(subMessage)), ex.getCause()));
    }
    return Mono.error(new BookingTimeoutException(
        provideTimeoutErrorMessage("attempting to %s".formatted(subMessage)), ex));
  }

  @SuppressWarnings("all")
  private Flux<Booking> handleRepositoryFluxTimeout(Throwable ex, String subMessage) {
    if (Exceptions.isRetryExhausted(ex)) {
      return Flux.error(new BookingTimeoutException(
          provideTimeoutErrorMessage(
              "attempting to %s . Exhausted all retries".formatted(subMessage)),
          ex.getCause()));
    }
    return Flux.error(new BookingTimeoutException(
        provideTimeoutErrorMessage("attempting to %s".formatted(subMessage)), ex));
  }

  private Mono<Void> handlePostingTimeout(Throwable ex, Integer bookId, String subMessage) {
    if (Exceptions.isRetryExhausted(ex)) {
      return Mono.error(new KafkaPostingException(
          providePostingTimeoutErrorMessage(
              "attempting to post %s message for book [%d]. Exhausted all retries".formatted(
                  subMessage, bookId)), ex.getCause()));
    }
    return Mono.error(new KafkaPostingException(
        providePostingTimeoutErrorMessage(
            "attempting to post %s message for book [%d]".formatted(subMessage, bookId)), ex));
  }

  private String providePostingTimeoutErrorMessage(String subMessage) {
    return String.format("Message posting for booking timed out [over %d milliseconds] %s",
        bookingTimeoutDuration.toMillis(), subMessage);
  }

  private String provideTimeoutErrorMessage(String subMessage) {
    return String.format("Booking repository timed out [over %d milliseconds] %s",
        bookingRepositoryTimeoutInMilliseconds, subMessage);
  }

  private BookingId dtoToBookingId(BookingDto dto) {
    BookingId bookingId = new BookingId();
    bookingId.setId(dto.getId());
    bookingId.setUsername(dto.getUsername());
    bookingId.setEmail(dto.getEmail());
    return bookingId;
  }
}

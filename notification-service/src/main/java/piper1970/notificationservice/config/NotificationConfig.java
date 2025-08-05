package piper1970.notificationservice.config;

import static org.springframework.security.config.Customizer.withDefaults;

import brave.Tracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import piper1970.eventservice.common.kafka.TopicCreater;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.routehandler.BookingConfirmationHandler;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.kafka.receiver.MicrometerConsumerListener;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.observation.KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.MicrometerProducerListener;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.observation.KafkaSenderObservation.DefaultKafkaSenderObservationConvention;
import reactor.util.retry.Retry;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableWebFluxSecurity
public class NotificationConfig {

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final ObjectMapper objectMapper;

  private final Integer replicationFactor;
  private final Integer partitionCount;
  private final String kafkaRetentionProperty;
  private final Duration notificationTimeoutDuration;

  public NotificationConfig(BookingConfirmationRepository bookingConfirmationRepository,
      ObjectMapper objectMapper,
      @Value("${kafka.replication.factor}") Integer replicationFactor,
      @Value("${kafka.partition.count}") Integer partitionCount,
      @Value("${kafka.retention.days}") Integer retentionDays,
      @Value("${notification-repository.timeout.milliseconds}") Long notificationRepositoryTimeoutInMilliseconds
      ) {
    this.bookingConfirmationRepository = bookingConfirmationRepository;
    this.objectMapper = objectMapper;
    this.replicationFactor = replicationFactor;
    this.partitionCount = partitionCount;
    this.kafkaRetentionProperty = String.valueOf(Duration.ofDays(retentionDays).toMillis());
    notificationTimeoutDuration = Duration.ofMillis(notificationRepositoryTimeoutInMilliseconds);
  }

  //region Mustache Template Factory

  @Bean
  public MustacheFactory mustacheFactory() {
    return new DefaultMustacheFactory();
  }

  //endregion Mustache Template Factory

  //region Route Handling

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  public RouterFunction<ServerResponse> route(
      BookingConfirmationHandler bookingConfirmationHandler) {
    return RouterFunctions.route()
        .GET("/api/notifications/confirm/{confirmationString}",
            bookingConfirmationHandler::handleConfirmation)
        .build();
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable)
        .cors(withDefaults())
        .authorizeExchange(exchange -> exchange
            .pathMatchers(HttpMethod.GET, "/actuator/**", "/api/notifications/confirm/**")
            .permitAll()
            .anyExchange()
            .denyAll());

    return http.build();
  }

  @Bean
  public BookingConfirmationHandler bookingConfirmationHandler(
      MessagePostingService messagePostingService,
      Clock clock,
      @Value("${confirmation-handler.retries.max:2}") long maxRetries,
      @Qualifier("repository") Retry defaultRepositoryRetry,
      @Qualifier("confirmations") Counter confirmationSuccessCounter,
      @Qualifier("expirations") Counter expirationsCounter) {
    return new BookingConfirmationHandler(
        bookingConfirmationRepository,
        messagePostingService,
        objectMapper,
        confirmationSuccessCounter,
        expirationsCounter,
        clock,
        notificationTimeoutDuration,
        maxRetries,
        defaultRepositoryRetry
    );
  }

  @Bean
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.addAllowedOriginPattern("*");
    config.addAllowedHeader("*");
    config.setAllowedMethods(List.of("GET", "OPTIONS"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }

  //endregion Rout Handling

  //region Kafka Setup

  //region Kafka Topic Creation

  @Bean
  TopicCreater topicCreater() {
    return new TopicCreater(partitionCount, replicationFactor, kafkaRetentionProperty);
  }

  //region Producer Topics

  @Bean
  NewTopic createBookingConfirmedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CONFIRMED);
  }

  @Bean
  NewTopic createBookingExpiredTopic(TopicCreater topicCreater){
    return topicCreater.create(Topics.BOOKING_EXPIRED);
  }

  //endregion Producer Topics

  //region Consumer Topics

  @Bean
  NewTopic createBookingCreatedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CREATED);
  }

  @Bean
  NewTopic createBookingEventUnavailableTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_EVENT_UNAVAILABLE);
  }

  @Bean
  NewTopic createBookingCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKING_CANCELLED);
  }

  @Bean
  NewTopic createBookingsUpdatedTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKINGS_UPDATED);
  }

  @Bean
  NewTopic createBookingsCancelledTopic(TopicCreater topicCreater) {
    return topicCreater.create(Topics.BOOKINGS_CANCELLED);
  }

  //endregion Consumer Topics

  //endregion Kafka Topic Creation

  //region Producer

  @Bean
  KafkaSender<Integer, Object> kafkaSender(KafkaProperties kafkaProperties, ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    var senderOptions = SenderOptions.<Integer, Object>create(propertiesMap)
        .producerListener(new MicrometerProducerListener(meterRegistry))
        .withObservation(observationRegistry,
            new DefaultKafkaSenderObservationConvention()); // needed for capturing correlation-id in spanned logs
    return KafkaSender.create(senderOptions);
  }

  @Bean
  DeadLetterTopicProducer deadLetterTopicProducer(KafkaSender<Integer, Object> kafkaSender,
      Tracer tracer, @Value("${kafka.dlt.suffix:-ns-dlt}") String deadLetterTopicSuffix,
      Clock clock) {
    return new DeadLetterTopicProducer(kafkaSender, tracer, deadLetterTopicSuffix, clock);
  }

  //endregion Producer

  //region Kafka Consumer

  @Bean
  public BaseListenerOptions baseListenerOptions(ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      JavaMailSender mailSender,
      MustacheFactory mustacheFactory,
      @Value("${mustache.location:templates}") String mustacheLocation,
      @Value("${mail.message.from}") String fromAddress,
      @Value("${events.api.address: http://localhost:8080/api/events}") String eventsApiAddress,
      @Value("${bookings.api.address: http://localhost:8080/api/bookings}") String bookingsApiAddress,
      @Value("${mail.send.timeout.milliseconds:10000}") Long mailSendTimeoutMillis,
      @Value("${mail.delay.milliseconds:500}") Long mailDelayMilliseconds
      ) {
    return BaseListenerOptions.builder()
        .bookingsApiAddress(bookingsApiAddress)
        .eventsApiAddress(eventsApiAddress)
        .deadLetterTopicProducer(deadLetterTopicProducer)
        .reactiveKafkaReceiverFactory(reactiveKafkaReceiverFactory)
        .fromAddress(fromAddress)
        .mustacheFactory(mustacheFactory)
        .mustacheLocation(mustacheLocation)
        .mailSender(mailSender)
        .mailSendTimeoutMillis(mailSendTimeoutMillis)
        .mailDelayMillis(mailDelayMilliseconds)
        .build();
  }


  @Bean
  public ReceiverOptions<Integer, Object> receiverOptions(KafkaProperties kafkaProperties, ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    return ReceiverOptions.<Integer, Object>create(kafkaProperties.buildConsumerProperties())
        .consumerListener(new MicrometerConsumerListener(meterRegistry))
        .withObservation(observationRegistry,
            new DefaultKafkaReceiverObservationConvention()); // needed for capturing correlation-id in spanned logs
  }

  @Bean
  public ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory(ReceiverOptions<Integer, Object> receiverOptions) {
    var topics = List.of(Topics.BOOKING_CREATED, Topics.BOOKING_EVENT_UNAVAILABLE, Topics.BOOKING_CANCELLED,
        Topics.BOOKINGS_UPDATED, Topics.BOOKINGS_CANCELLED);
    return new ReactiveKafkaReceiverFactory(receiverOptions, topics);
  }

  //endregion Kafka Consumer

  //endregion Kafka Setup

  //region Reactive Retries

  @Bean
  @Qualifier("repository")
  public Retry defaultRepositoryRetry(
      @Value("${repository.retry.max.attempts:3}") long maxAttempts,
      @Value("${repository.retry.duration.millis:500}") long durationInMillis,
      @Value("${repository.retry.jitter.factor:0.7D}")double jitterFactor
  ){
    return Retry.backoff(maxAttempts, Duration.ofMillis(durationInMillis))
        .filter(throwable -> throwable instanceof TimeoutException)
        .jitter(jitterFactor);
  }

  @Bean
  @Qualifier("kafka")
  public Retry defaultKafkaRetry(
      @Value("${kafka.retry.max.attempts:3}") long maxAttempts,
      @Value("${kafka.retry.duration.millis:500}") long durationInMillis,
      @Value("${kafka.retry.jitter.factor:0.7D}")double jitterFactor
  ){
    return Retry.backoff(maxAttempts, Duration.ofMillis(durationInMillis))
        .filter(throwable -> throwable instanceof TimeoutException)
        .jitter(jitterFactor);
  }

  @Bean
  @Qualifier("mailer")
  public Retry defaultMailerRetry(
      @Value("${mailer.retry.max.attempts:3}") long maxAttempts,
      @Value("${mailer.retry.duration.millis:500}") long durationInMillis,
      @Value("${mailer.retry.jitter.factor:0.7D}")double jitterFactor
  ){
    return Retry.backoff(maxAttempts, Duration.ofMillis(durationInMillis))
        .filter(throwable -> throwable instanceof TimeoutException)
        .jitter(jitterFactor);
  }

  //endregion Reactive Retries

}

package piper1970.notificationservice.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
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
import piper1970.eventservice.common.topics.Topics;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.routehandler.BookingConfirmationHandler;
import piper1970.notificationservice.service.KafkaMessagePostingService;
import piper1970.notificationservice.service.MessagePostingService;

@Configuration
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
      @Value("${notification-repository.timout.milliseconds}") Long notificationRepositoryTimeoutInMilliseconds) {
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
      Clock clock) {
    return new BookingConfirmationHandler(
        bookingConfirmationRepository,
        messagePostingService,
        objectMapper,
        clock,
        notificationTimeoutDuration
    );
  }

  @Bean
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.addAllowedOrigin("*");
    config.setAllowedMethods(List.of("GET"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }

  //endregion Rout Handling

  //region Kafka Setup

  @Bean
  public MessagePostingService messagePostingService(KafkaTemplate<Integer, Object> kafkaTemplate) {
    return new KafkaMessagePostingService(kafkaTemplate);
  }

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

  //region KafkaTemplate Setup

  @Bean
  ProducerFactory<Integer, Object> producerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> propertiesMap = kafkaProperties.buildProducerProperties();
    return new DefaultKafkaProducerFactory<>(propertiesMap);
  }

  @Bean
  KafkaTemplate<Integer, Object> kafkaTemplate(ProducerFactory<Integer, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  //endregion KafkaTemplate Setup

  //region Kafka Consumers Setup

  @Bean
  public ConsumerFactory<Integer, Object> consumerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> propertiesMap = kafkaProperties.buildConsumerProperties();
    return new DefaultKafkaConsumerFactory<>(propertiesMap);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<Integer, Object> kafkaListenerContainerFactory(
      ConsumerFactory<Integer, Object> consumerFactory
  ) {
    ConcurrentKafkaListenerContainerFactory<Integer, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setConcurrency(partitionCount * 5); // 5 consumer topics X # of partitions
    // factory.getContainerProperties().setPollTimeout(X)
//    factory.getContainerProperties().setListenerTaskExecutor(new VirtualThreadTaskExecutor());
    return factory;
  }

  //endregion Kafka Consumers Setup

  //endregion Kafka Setup

}

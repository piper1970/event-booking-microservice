package piper1970.notificationservice.kafka.listener.options;


import com.github.mustachejava.MustacheFactory;
import lombok.Builder;
import lombok.Value;
import org.springframework.mail.javamail.JavaMailSender;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;

@Value
@Builder
public class BaseListenerOptions {
  ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory;
  DeadLetterTopicProducer deadLetterTopicProducer;
  MustacheFactory mustacheFactory;
  JavaMailSender mailSender;
  String mustacheLocation;
  String fromAddress;
  String bookingsApiAddress;
  String eventsApiAddress;
  Long mailSendTimeoutMillis;
  Long mailDelayMillis;
}

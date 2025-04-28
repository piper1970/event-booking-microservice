package piper1970.notificationservice.kafka.listener;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.io.Resources;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Helper abstract method to deal with common constructor logic
 */
public abstract class AbstractListener extends DiscoverableListener {

  protected final JavaMailSender mailSender;
  protected final MustacheFactory mustacheFactory;
  protected final String mustacheLocation;
  protected final String fromAddress;
  protected final String bookingsApiAddress;
  protected final String eventsApiAddress;

  public AbstractListener(BaseListenerOptions options) {
    super(options.getReactiveKafkaReceiverFactory(), options.getDeadLetterTopicProducer());
    this.mailSender = options.getMailSender();
    this.mustacheFactory = options.getMustacheFactory();
    this.mustacheLocation = options.getMustacheLocation();
    this.fromAddress = options.getFromAddress();
    this.bookingsApiAddress = options.getBookingsApiAddress();
    this.eventsApiAddress = options.getEventsApiAddress();
  }

  abstract protected Logger getLogger();

  /// Provides flux setup for proper auto-close behavior of Reader resource Used by Flux.using() as
  /// the initial supplier parameter
  protected Flux<EmailTemplate> readerFlux(String template, Stream<PropsHolder> propHolders) {
    return Flux.using(readerSupplier(template),
        reader -> buildEmailsFromMustacheAsFlux(reader, template, propHolders)
    );
  }

  /// Provides mono setup for proper auto-close behavior of Reader resource
  protected Mono<String> readerMono(String template, Object props) {
    return Mono.using(readerSupplier(template),
        reader -> buildEmailFromMustacheAsMono(reader, template, props)
    );
  }

  protected void logMailDelivery(CharSequence memberEmail, String formattedEmail) {
    getLogger().info("Mail sent to {}:  {}", memberEmail, formattedEmail);
  }

  protected Mono<Void> handleMailMono(String email, String subject, String body) {
    return Mono.fromCallable(() -> sendMail(email, subject, body))
        .subscribeOn(Schedulers.boundedElastic());
  }

  /// Helper method to box-then-unbox from flux-mono-flux, to allow for using the fromRunnable logic
  /// in deferring blocking sendMail logic
  protected Flux<Void> handleMailFlux(EmailTemplate tpl, String topic) {
    return Flux.defer(() -> Mono.fromCallable(() -> sendMail(tpl.subject().toString(), topic,
                tpl.body())
            )
            .flux())
        .subscribeOn(Schedulers.boundedElastic());
  }

  protected record EmailTemplate(Object subject, String body) {
  }

  protected record PropsHolder(String email, Object props) {
  }

  protected String buildBookingLink(Integer bookingId) {
    return bookingsApiAddress + "/" + bookingId;
  }

  protected String buildEventLink(Integer eventId) {
    return eventsApiAddress + "/" + eventId;
  }

  private Flux<EmailTemplate> buildEmailsFromMustacheAsFlux(
      Reader reader, String template,
      Stream<PropsHolder> propsHolderStream) {
    Mustache mustache = mustacheFactory.compile(reader, template);
    return Flux.fromStream(propsHolderStream)
        .subscribeOn(Schedulers.parallel())
        .map(propsHolder -> {
              var msg = buildEmailFromMustache(mustache, propsHolder.props());
              return new EmailTemplate(propsHolder.email(), msg);
            }
        );
  }

  /// Used as first supplier parameter by Mono.using() and Flux.using()
  private Callable<Reader> readerSupplier(String resource) {
    var fullPath = mustacheLocation + "/" + resource;
    return () -> new InputStreamReader(Resources.getResource(fullPath).openStream());
  }

  /// Used as second parameter to Mono.using() to build Mustache templating logic from Reader
  /// resource
  private Mono<String> buildEmailFromMustacheAsMono(Reader reader, String template, Object props) {
    return Mono.fromCallable(() -> {
      Mustache mustache = mustacheFactory.compile(reader, template);
      return buildEmailFromMustache(mustache, props);
    }).subscribeOn(Schedulers.parallel());
  }

  private String buildEmailFromMustache(Mustache mustache, Object prop) {
    StringWriter writer = new StringWriter();
    mustache.execute(writer, prop);
    return writer.toString();
  }

  private Void sendMail(String to, String subject, String body) {

    try {
      MimeMessage message = mailSender.createMimeMessage();
      message.setSubject(subject);
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setTo(to);
      helper.setText(body, true);
      helper.setFrom(fromAddress);
      mailSender.send(message);
      return null;
    } catch (Exception e) {
      getLogger().error("Unable to send email", e);
      throw new RuntimeException(e);
    }
  }
}

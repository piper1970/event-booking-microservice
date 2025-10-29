package piper1970.eventservice.common.kafka.reactive;

import static piper1970.eventservice.common.kafka.reactive.TracingHelper.decorateWithTracing;

import java.util.function.Function;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;

/**
 * Abstract class for building Kafka listeners. Implementing classes must provider topi, subscription (for disposing),
 * initialization of receiver flux, and individual record handline functionality.
 */
public abstract class DiscoverableListener implements DisposableBean, AutoCloseable {

  private final ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory;
  private final DeadLetterTopicProducer deadLetterTopicProducer;

  public DiscoverableListener(ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer) {
    this.reactiveKafkaReceiverFactory = reactiveKafkaReceiverFactory;
    this.deadLetterTopicProducer = deadLetterTopicProducer;
  }

  /**
   * Handle the Flux initialization behavior.
   * <p/>
   * Typically, this starts with a call to {@link #buildFluxRequest}, followed by a call to subscribe(),
   * which would return a subscription/disposable component for possible cancellation of the request pipeline.
   */
  public abstract void initializeReceiverFlux();

  /**
   * @return topic the listener is following
   */
  protected abstract String getTopic();

  /**
   * @return disposable subscription, used for possible cancellation of the Flux pipeline.
   */
  protected abstract Disposable getSubscription();

  /**
   * Function for handling individual requests from the Flux pipeline. Called at end of {@link #buildFluxRequest}
   *
   * @param record Current ReceiverRecord for processing
   * @return Mono[ReceiverRecord] for downstream processing
   */
  protected abstract Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(ReceiverRecord<Integer, Object> record);

  /**
   * Handle the main flux build.
   * <p/>
   * Relies on abstract @handleIndividualRequest logic for individual message processing.
   * <p/>
   * To ensure proper trace propagation in kafka receivers, handleIndividualRequest is decorated by
   * {@link TracingHelper#decorateWithTracing(ReceiverRecord, Function)}
   *
   * @return Flux[ReceiverRecord[Integer,Object]]
   */
  protected Flux<ReceiverRecord<Integer, Object>> buildFluxRequest() {
    return createReceiver()
        .receive()
        .subscribeOn(Schedulers.boundedElastic())
        // enable trace propagation
        .concatMap(record -> decorateWithTracing(record, this::handleIndividualRequest));
  }

  private KafkaReceiver<Integer, Object> createReceiver() {
    return reactiveKafkaReceiverFactory
        .getReceiver(getTopic());
  }

  protected Mono<ReceiverRecord<Integer, Object>> handleDLTLogic(ReceiverRecord<Integer, Object> record){
    return deadLetterTopicProducer.process(record)
        .subscribeOn(Schedulers.boundedElastic())
        .then(Mono.just(record));
  }

  @Override
  public void close(){
    destroy();
  }


  @Override
  public void destroy(){
    var subscription = getSubscription();
    if(subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
    }
  }
}

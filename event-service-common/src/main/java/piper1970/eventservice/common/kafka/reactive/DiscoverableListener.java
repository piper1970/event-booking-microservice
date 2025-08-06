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

public abstract class DiscoverableListener implements DisposableBean, AutoCloseable {

  private final ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory;
  private final DeadLetterTopicProducer deadLetterTopicProducer;

  public DiscoverableListener(ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer) {
    this.reactiveKafkaReceiverFactory = reactiveKafkaReceiverFactory;
    this.deadLetterTopicProducer = deadLetterTopicProducer;
  }

  public abstract void initializeReceiverFlux();
  protected abstract String getTopic();
  protected abstract Disposable getSubscription();
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

package piper1970.eventservice.common.kafka.reactive;

import brave.Span;
import brave.Tracer;
import java.util.ArrayList;
import java.util.function.Function;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

public class TracingHelper {

  /**
   * Decorator for kafka message processing that ensures trace/span propagation works for Slf4J logging
   * @param record Original received kafka record
   * @param delegate Original message handler
   * @return Mono of type ReceiverRecord
   */
  public static Mono<ReceiverRecord<Integer, Object>> decorateWithTracing(ReceiverRecord<Integer, Object> record,
      Function<ReceiverRecord<Integer, Object>, Mono<ReceiverRecord<Integer, Object>>> delegate) {

    return Mono.deferContextual(context -> {
      // restore MDC after call
      var originalMDC = MDC.getCopyOfContextMap();
      var headers = record.headers();

      try{
        var traceIdHeader = headers.lastHeader("X-Trace-Id");
        var spanIdHeader = headers.lastHeader("X-Span-Id");
        var b3TraceIdHeader = headers.lastHeader("X-B3-TraceId");
        var b3SpanIdHeader = headers.lastHeader("X-B3-SpanId");

        // handle traceId propagation, preferring brave's b3-traceid property over standard traceId property
        if(b3TraceIdHeader != null){
          MDC.put("traceId", new String(b3TraceIdHeader.value()));
        }else if(traceIdHeader != null){
          MDC.put("traceId", new String(traceIdHeader.value()));
        }

        // handle spanID propagation, preferring brave's b3-spanId property over standard spanId property
        if(b3SpanIdHeader != null){
          MDC.put("spanId", new String(b3SpanIdHeader.value()));
        }else if(spanIdHeader != null){
          MDC.put("spanId", new String(spanIdHeader.value()));
        }
        return delegate.apply(record);
      }finally {
        MDC.clear();
        if(originalMDC != null){ // restore original MDC settings, if present
          MDC.setContextMap(originalMDC);
        }
      }
    });
  }

  /**
   * Extracts traceId & spanId components from brave's Tracer, translating them into Kafka Headers.
   *
   * @see Tracer
   * @return Iterable of Headers, with traceId & spanId headers attached
   */
  public static Iterable<Header> extractMDCIntoHeaders(Tracer tracer){

    var headers = new ArrayList<Header>();

    Span currentSpan = tracer.currentSpan();
    if(currentSpan != null){
      var context = currentSpan.context();
      String traceId = context.traceIdString();
      String spanId = context.spanIdString();

      headers.add(new RecordHeader("X-Trace-Id", traceId.getBytes()));
      headers.add(new RecordHeader("X-B3-Trace-Id", traceId.getBytes()));

      headers.add(new RecordHeader("X-Span-Id", spanId.getBytes()));
      headers.add(new RecordHeader("X-B3-Span-Id", spanId.getBytes()));
    }

    return headers;
  }
}

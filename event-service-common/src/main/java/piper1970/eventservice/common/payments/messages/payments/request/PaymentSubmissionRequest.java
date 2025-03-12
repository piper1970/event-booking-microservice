package piper1970.eventservice.common.payments.messages.payments.request;

import java.math.BigDecimal;
import org.springframework.lang.NonNull;

public record PaymentSubmissionRequest(
    @NonNull Integer bookingId,
    @NonNull BigDecimal cost
) {
  static PaymentSubmissionRequest of(@NonNull Integer bookingId,
      @NonNull BigDecimal cost) {
    return new PaymentSubmissionRequest(bookingId, cost);
  }
}

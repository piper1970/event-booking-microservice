package piper1970.eventservice.common.payments.messages.refunds.request;

import java.util.UUID;

public record PaymentRefundRequest(
    Integer bookingId,
    UUID paymentToken
) {
  static PaymentRefundRequest of(
      Integer bookingId,
      UUID paymentToken
  ){
    return new PaymentRefundRequest(bookingId, paymentToken);
  }
}

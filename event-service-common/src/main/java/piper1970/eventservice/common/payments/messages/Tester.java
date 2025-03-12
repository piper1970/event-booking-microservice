package piper1970.eventservice.common.payments.messages;

import java.util.UUID;
import piper1970.eventservice.common.payments.messages.payments.response.PaymentSucceeded;

public class Tester {

  public static void main(String[] args) {
    var paymentSuccess = PaymentSucceeded.builder()
        .bookingId(1)
        .extraMessage("test")
        .paymentToken(UUID.randomUUID())
        .build();
  }
}

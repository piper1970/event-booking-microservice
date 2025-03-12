package piper1970.eventservice.common.payments.messages.payments.response;

import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
public class PaymentSucceeded extends PaymentResponse {
  UUID paymentToken;
  String extraMessage;
}

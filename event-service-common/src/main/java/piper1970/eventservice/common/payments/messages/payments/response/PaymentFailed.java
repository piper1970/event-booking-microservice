package piper1970.eventservice.common.payments.messages.payments.response;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
public class PaymentFailed extends PaymentResponse {
  String responseMessage;
  String errorCode;
}

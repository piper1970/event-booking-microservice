package piper1970.eventservice.common.payments.messages.refunds.response;

import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
public class RefundSucceeded extends RefundResponse{
  UUID refundToken;
}

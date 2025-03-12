package piper1970.eventservice.common.payments.messages.payments.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract sealed class PaymentResponse permits PaymentFailed,
    PaymentSucceeded {
  Integer bookingId;
}

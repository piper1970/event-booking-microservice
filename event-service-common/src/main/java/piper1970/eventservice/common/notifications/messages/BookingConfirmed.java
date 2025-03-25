/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package piper1970.eventservice.common.notifications.messages;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class BookingConfirmed extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 6665270207383021194L;


  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"BookingConfirmed\",\"namespace\":\"piper1970.eventservice.common.notifications.messages\",\"fields\":[{\"name\":\"booking\",\"type\":{\"type\":\"record\",\"name\":\"BookingId\",\"namespace\":\"piper1970.eventservice.common.bookings.messages.types\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"Email\",\"type\":\"string\"},{\"name\":\"username\",\"type\":\"string\"}]}},{\"name\":\"eventId\",\"type\":\"int\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static final SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<BookingConfirmed> ENCODER =
      new BinaryMessageEncoder<>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<BookingConfirmed> DECODER =
      new BinaryMessageDecoder<>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<BookingConfirmed> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<BookingConfirmed> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<BookingConfirmed> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this BookingConfirmed to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a BookingConfirmed from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a BookingConfirmed instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static BookingConfirmed fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  private piper1970.eventservice.common.bookings.messages.types.BookingId booking;
  private int eventId;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public BookingConfirmed() {}

  /**
   * All-args constructor.
   * @param booking The new value for booking
   * @param eventId The new value for eventId
   */
  public BookingConfirmed(piper1970.eventservice.common.bookings.messages.types.BookingId booking, java.lang.Integer eventId) {
    this.booking = booking;
    this.eventId = eventId;
  }

  @Override
  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }

  @Override
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }

  // Used by DatumWriter.  Applications should not call.
  @Override
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return booking;
    case 1: return eventId;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @Override
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: booking = (piper1970.eventservice.common.bookings.messages.types.BookingId)value$; break;
    case 1: eventId = (java.lang.Integer)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'booking' field.
   * @return The value of the 'booking' field.
   */
  public piper1970.eventservice.common.bookings.messages.types.BookingId getBooking() {
    return booking;
  }


  /**
   * Sets the value of the 'booking' field.
   * @param value the value to set.
   */
  public void setBooking(piper1970.eventservice.common.bookings.messages.types.BookingId value) {
    this.booking = value;
  }

  /**
   * Gets the value of the 'eventId' field.
   * @return The value of the 'eventId' field.
   */
  public int getEventId() {
    return eventId;
  }


  /**
   * Sets the value of the 'eventId' field.
   * @param value the value to set.
   */
  public void setEventId(int value) {
    this.eventId = value;
  }

  /**
   * Creates a new BookingConfirmed RecordBuilder.
   * @return A new BookingConfirmed RecordBuilder
   */
  public static piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder newBuilder() {
    return new piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder();
  }

  /**
   * Creates a new BookingConfirmed RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new BookingConfirmed RecordBuilder
   */
  public static piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder newBuilder(piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder other) {
    if (other == null) {
      return new piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder();
    } else {
      return new piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder(other);
    }
  }

  /**
   * Creates a new BookingConfirmed RecordBuilder by copying an existing BookingConfirmed instance.
   * @param other The existing instance to copy.
   * @return A new BookingConfirmed RecordBuilder
   */
  public static piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder newBuilder(piper1970.eventservice.common.notifications.messages.BookingConfirmed other) {
    if (other == null) {
      return new piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder();
    } else {
      return new piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder(other);
    }
  }

  /**
   * RecordBuilder for BookingConfirmed instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<BookingConfirmed>
    implements org.apache.avro.data.RecordBuilder<BookingConfirmed> {

    private piper1970.eventservice.common.bookings.messages.types.BookingId booking;
    private piper1970.eventservice.common.bookings.messages.types.BookingId.Builder bookingBuilder;
    private int eventId;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$, MODEL$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.booking)) {
        this.booking = data().deepCopy(fields()[0].schema(), other.booking);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasBookingBuilder()) {
        this.bookingBuilder = piper1970.eventservice.common.bookings.messages.types.BookingId.newBuilder(other.getBookingBuilder());
      }
      if (isValidValue(fields()[1], other.eventId)) {
        this.eventId = data().deepCopy(fields()[1].schema(), other.eventId);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing BookingConfirmed instance
     * @param other The existing instance to copy.
     */
    private Builder(piper1970.eventservice.common.notifications.messages.BookingConfirmed other) {
      super(SCHEMA$, MODEL$);
      if (isValidValue(fields()[0], other.booking)) {
        this.booking = data().deepCopy(fields()[0].schema(), other.booking);
        fieldSetFlags()[0] = true;
      }
      this.bookingBuilder = null;
      if (isValidValue(fields()[1], other.eventId)) {
        this.eventId = data().deepCopy(fields()[1].schema(), other.eventId);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'booking' field.
      * @return The value.
      */
    public piper1970.eventservice.common.bookings.messages.types.BookingId getBooking() {
      return booking;
    }


    /**
      * Sets the value of the 'booking' field.
      * @param value The value of 'booking'.
      * @return This builder.
      */
    public piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder setBooking(piper1970.eventservice.common.bookings.messages.types.BookingId value) {
      validate(fields()[0], value);
      this.bookingBuilder = null;
      this.booking = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'booking' field has been set.
      * @return True if the 'booking' field has been set, false otherwise.
      */
    public boolean hasBooking() {
      return fieldSetFlags()[0];
    }

    /**
     * Gets the Builder instance for the 'booking' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public piper1970.eventservice.common.bookings.messages.types.BookingId.Builder getBookingBuilder() {
      if (bookingBuilder == null) {
        if (hasBooking()) {
          setBookingBuilder(piper1970.eventservice.common.bookings.messages.types.BookingId.newBuilder(booking));
        } else {
          setBookingBuilder(piper1970.eventservice.common.bookings.messages.types.BookingId.newBuilder());
        }
      }
      return bookingBuilder;
    }

    /**
     * Sets the Builder instance for the 'booking' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder setBookingBuilder(piper1970.eventservice.common.bookings.messages.types.BookingId.Builder value) {
      clearBooking();
      bookingBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'booking' field has an active Builder instance
     * @return True if the 'booking' field has an active Builder instance
     */
    public boolean hasBookingBuilder() {
      return bookingBuilder != null;
    }

    /**
      * Clears the value of the 'booking' field.
      * @return This builder.
      */
    public piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder clearBooking() {
      booking = null;
      bookingBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'eventId' field.
      * @return The value.
      */
    public int getEventId() {
      return eventId;
    }


    /**
      * Sets the value of the 'eventId' field.
      * @param value The value of 'eventId'.
      * @return This builder.
      */
    public piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder setEventId(int value) {
      validate(fields()[1], value);
      this.eventId = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'eventId' field has been set.
      * @return True if the 'eventId' field has been set, false otherwise.
      */
    public boolean hasEventId() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'eventId' field.
      * @return This builder.
      */
    public piper1970.eventservice.common.notifications.messages.BookingConfirmed.Builder clearEventId() {
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BookingConfirmed build() {
      try {
        BookingConfirmed record = new BookingConfirmed();
        if (bookingBuilder != null) {
          try {
            record.booking = this.bookingBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("booking"));
            throw e;
          }
        } else {
          record.booking = fieldSetFlags()[0] ? this.booking : (piper1970.eventservice.common.bookings.messages.types.BookingId) defaultValue(fields()[0]);
        }
        record.eventId = fieldSetFlags()[1] ? this.eventId : (java.lang.Integer) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<BookingConfirmed>
    WRITER$ = (org.apache.avro.io.DatumWriter<BookingConfirmed>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<BookingConfirmed>
    READER$ = (org.apache.avro.io.DatumReader<BookingConfirmed>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    this.booking.customEncode(out);

    out.writeInt(this.eventId);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (this.booking == null) {
        this.booking = new piper1970.eventservice.common.bookings.messages.types.BookingId();
      }
      this.booking.customDecode(in);

      this.eventId = in.readInt();

    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (this.booking == null) {
            this.booking = new piper1970.eventservice.common.bookings.messages.types.BookingId();
          }
          this.booking.customDecode(in);
          break;

        case 1:
          this.eventId = in.readInt();
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}











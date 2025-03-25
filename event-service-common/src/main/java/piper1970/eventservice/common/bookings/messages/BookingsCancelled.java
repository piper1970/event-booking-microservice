/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package piper1970.eventservice.common.bookings.messages;

import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class BookingsCancelled extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 2182325694613126009L;


  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"BookingsCancelled\",\"namespace\":\"piper1970.eventservice.common.bookings.messages\",\"fields\":[{\"name\":\"bookings\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"BookingId\",\"namespace\":\"piper1970.eventservice.common.bookings.messages.types\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"},{\"name\":\"Email\",\"type\":\"string\"},{\"name\":\"username\",\"type\":\"string\"}]}}},{\"name\":\"eventId\",\"type\":\"int\"},{\"name\":\"message\",\"type\":[\"null\",\"string\"]}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static final SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<BookingsCancelled> ENCODER =
      new BinaryMessageEncoder<>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<BookingsCancelled> DECODER =
      new BinaryMessageDecoder<>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<BookingsCancelled> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<BookingsCancelled> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<BookingsCancelled> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this BookingsCancelled to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a BookingsCancelled from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a BookingsCancelled instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static BookingsCancelled fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  private java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> bookings;
  private int eventId;
  private java.lang.CharSequence message;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public BookingsCancelled() {}

  /**
   * All-args constructor.
   * @param bookings The new value for bookings
   * @param eventId The new value for eventId
   * @param message The new value for message
   */
  public BookingsCancelled(java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> bookings, java.lang.Integer eventId, java.lang.CharSequence message) {
    this.bookings = bookings;
    this.eventId = eventId;
    this.message = message;
  }

  @Override
  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }

  @Override
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }

  // Used by DatumWriter.  Applications should not call.
  @Override
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return bookings;
    case 1: return eventId;
    case 2: return message;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @Override
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: bookings = (java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId>)value$; break;
    case 1: eventId = (java.lang.Integer)value$; break;
    case 2: message = (java.lang.CharSequence)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'bookings' field.
   * @return The value of the 'bookings' field.
   */
  public java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> getBookings() {
    return bookings;
  }


  /**
   * Sets the value of the 'bookings' field.
   * @param value the value to set.
   */
  public void setBookings(java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> value) {
    this.bookings = value;
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
   * Gets the value of the 'message' field.
   * @return The value of the 'message' field.
   */
  public java.lang.CharSequence getMessage() {
    return message;
  }


  /**
   * Sets the value of the 'message' field.
   * @param value the value to set.
   */
  public void setMessage(java.lang.CharSequence value) {
    this.message = value;
  }

  /**
   * Creates a new BookingsCancelled RecordBuilder.
   * @return A new BookingsCancelled RecordBuilder
   */
  public static piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder newBuilder() {
    return new piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder();
  }

  /**
   * Creates a new BookingsCancelled RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new BookingsCancelled RecordBuilder
   */
  public static piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder newBuilder(piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder other) {
    if (other == null) {
      return new piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder();
    } else {
      return new piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder(other);
    }
  }

  /**
   * Creates a new BookingsCancelled RecordBuilder by copying an existing BookingsCancelled instance.
   * @param other The existing instance to copy.
   * @return A new BookingsCancelled RecordBuilder
   */
  public static piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder newBuilder(piper1970.eventservice.common.bookings.messages.BookingsCancelled other) {
    if (other == null) {
      return new piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder();
    } else {
      return new piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder(other);
    }
  }

  /**
   * RecordBuilder for BookingsCancelled instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<BookingsCancelled>
    implements org.apache.avro.data.RecordBuilder<BookingsCancelled> {

    private java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> bookings;
    private int eventId;
    private java.lang.CharSequence message;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$, MODEL$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.bookings)) {
        this.bookings = data().deepCopy(fields()[0].schema(), other.bookings);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.eventId)) {
        this.eventId = data().deepCopy(fields()[1].schema(), other.eventId);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (isValidValue(fields()[2], other.message)) {
        this.message = data().deepCopy(fields()[2].schema(), other.message);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
    }

    /**
     * Creates a Builder by copying an existing BookingsCancelled instance
     * @param other The existing instance to copy.
     */
    private Builder(piper1970.eventservice.common.bookings.messages.BookingsCancelled other) {
      super(SCHEMA$, MODEL$);
      if (isValidValue(fields()[0], other.bookings)) {
        this.bookings = data().deepCopy(fields()[0].schema(), other.bookings);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.eventId)) {
        this.eventId = data().deepCopy(fields()[1].schema(), other.eventId);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.message)) {
        this.message = data().deepCopy(fields()[2].schema(), other.message);
        fieldSetFlags()[2] = true;
      }
    }

    /**
      * Gets the value of the 'bookings' field.
      * @return The value.
      */
    public java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> getBookings() {
      return bookings;
    }


    /**
      * Sets the value of the 'bookings' field.
      * @param value The value of 'bookings'.
      * @return This builder.
      */
    public piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder setBookings(java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> value) {
      validate(fields()[0], value);
      this.bookings = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'bookings' field has been set.
      * @return True if the 'bookings' field has been set, false otherwise.
      */
    public boolean hasBookings() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'bookings' field.
      * @return This builder.
      */
    public piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder clearBookings() {
      bookings = null;
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
    public piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder setEventId(int value) {
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
    public piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder clearEventId() {
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'message' field.
      * @return The value.
      */
    public java.lang.CharSequence getMessage() {
      return message;
    }


    /**
      * Sets the value of the 'message' field.
      * @param value The value of 'message'.
      * @return This builder.
      */
    public piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder setMessage(java.lang.CharSequence value) {
      validate(fields()[2], value);
      this.message = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'message' field has been set.
      * @return True if the 'message' field has been set, false otherwise.
      */
    public boolean hasMessage() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'message' field.
      * @return This builder.
      */
    public piper1970.eventservice.common.bookings.messages.BookingsCancelled.Builder clearMessage() {
      message = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BookingsCancelled build() {
      try {
        BookingsCancelled record = new BookingsCancelled();
        record.bookings = fieldSetFlags()[0] ? this.bookings : (java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId>) defaultValue(fields()[0]);
        record.eventId = fieldSetFlags()[1] ? this.eventId : (java.lang.Integer) defaultValue(fields()[1]);
        record.message = fieldSetFlags()[2] ? this.message : (java.lang.CharSequence) defaultValue(fields()[2]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<BookingsCancelled>
    WRITER$ = (org.apache.avro.io.DatumWriter<BookingsCancelled>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<BookingsCancelled>
    READER$ = (org.apache.avro.io.DatumReader<BookingsCancelled>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    long size0 = this.bookings.size();
    out.writeArrayStart();
    out.setItemCount(size0);
    long actualSize0 = 0;
    for (piper1970.eventservice.common.bookings.messages.types.BookingId e0: this.bookings) {
      actualSize0++;
      out.startItem();
      e0.customEncode(out);
    }
    out.writeArrayEnd();
    if (actualSize0 != size0)
      throw new java.util.ConcurrentModificationException("Array-size written was " + size0 + ", but element count was " + actualSize0 + ".");

    out.writeInt(this.eventId);

    if (this.message == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.message);
    }

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      long size0 = in.readArrayStart();
      java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> a0 = this.bookings;
      if (a0 == null) {
        a0 = new SpecificData.Array<piper1970.eventservice.common.bookings.messages.types.BookingId>((int)size0, SCHEMA$.getField("bookings").schema());
        this.bookings = a0;
      } else a0.clear();
      SpecificData.Array<piper1970.eventservice.common.bookings.messages.types.BookingId> ga0 = (a0 instanceof SpecificData.Array ? (SpecificData.Array<piper1970.eventservice.common.bookings.messages.types.BookingId>)a0 : null);
      for ( ; 0 < size0; size0 = in.arrayNext()) {
        for ( ; size0 != 0; size0--) {
          piper1970.eventservice.common.bookings.messages.types.BookingId e0 = (ga0 != null ? ga0.peek() : null);
          if (e0 == null) {
            e0 = new piper1970.eventservice.common.bookings.messages.types.BookingId();
          }
          e0.customDecode(in);
          a0.add(e0);
        }
      }

      this.eventId = in.readInt();

      if (in.readIndex() != 1) {
        in.readNull();
        this.message = null;
      } else {
        this.message = in.readString(this.message instanceof Utf8 ? (Utf8)this.message : null);
      }

    } else {
      for (int i = 0; i < 3; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          long size0 = in.readArrayStart();
          java.util.List<piper1970.eventservice.common.bookings.messages.types.BookingId> a0 = this.bookings;
          if (a0 == null) {
            a0 = new SpecificData.Array<piper1970.eventservice.common.bookings.messages.types.BookingId>((int)size0, SCHEMA$.getField("bookings").schema());
            this.bookings = a0;
          } else a0.clear();
          SpecificData.Array<piper1970.eventservice.common.bookings.messages.types.BookingId> ga0 = (a0 instanceof SpecificData.Array ? (SpecificData.Array<piper1970.eventservice.common.bookings.messages.types.BookingId>)a0 : null);
          for ( ; 0 < size0; size0 = in.arrayNext()) {
            for ( ; size0 != 0; size0--) {
              piper1970.eventservice.common.bookings.messages.types.BookingId e0 = (ga0 != null ? ga0.peek() : null);
              if (e0 == null) {
                e0 = new piper1970.eventservice.common.bookings.messages.types.BookingId();
              }
              e0.customDecode(in);
              a0.add(e0);
            }
          }
          break;

        case 1:
          this.eventId = in.readInt();
          break;

        case 2:
          if (in.readIndex() != 1) {
            in.readNull();
            this.message = null;
          } else {
            this.message = in.readString(this.message instanceof Utf8 ? (Utf8)this.message : null);
          }
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}











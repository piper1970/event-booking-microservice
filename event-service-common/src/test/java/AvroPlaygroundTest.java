public class AvroPlaygroundTest {

//  @Test
//  void avroPlayground() throws IOException {
//
//    // Writing to Avro
//    PaymentSubmissionRequest paymentSubmissionRequest = new PaymentSubmissionRequest();
//    paymentSubmissionRequest.setCost(new BigDecimal("100.00"));
//    paymentSubmissionRequest.setBookingId(1);
//
//    var serializedData = serialize(paymentSubmissionRequest);
//    System.out.println("Serialized Data\n" + new String(serializedData));
//
//    // Reading from Avro
//    var deserializedData = deserialize(serializedData);
//    System.out.println("Deserialized Data\n" + deserializedData.toString());
//  }

//  private byte[] serialize(PaymentSubmissionRequest paymentSubmissionRequest) throws IOException {
//    ByteArrayOutputStream baos = new ByteArrayOutputStream();
//    Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(PaymentSubmissionRequest.getClassSchema(), baos);
//    DatumWriter<PaymentSubmissionRequest> writer = new SpecificDatumWriter<>(PaymentSubmissionRequest.class);
//    writer.write(paymentSubmissionRequest, jsonEncoder);
//    jsonEncoder.flush();
//    return baos.toByteArray();
//  }
//
//  private PaymentSubmissionRequest deserialize(byte[] serializedData) throws IOException {
//    DatumReader<PaymentSubmissionRequest> reader = new SpecificDatumReader<>(PaymentSubmissionRequest.class);
//    Decoder decoder = DecoderFactory.get()
//        .jsonDecoder(PaymentSubmissionRequest.getClassSchema(), new String(serializedData));
//    return reader.read(null, decoder);
//  }
}

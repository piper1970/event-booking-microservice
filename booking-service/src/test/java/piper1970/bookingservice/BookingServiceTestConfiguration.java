package piper1970.bookingservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@SuppressWarnings("all")
public class BookingServiceTestConfiguration {

  // Postgres test-container set up from spring.r2dbc.url in properties file


  @Bean
  Network defaultNetwork() {
    return Network.newNetwork();
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafkaContainer(Network network) {
    return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0-rc3")
    ).withNetwork(network)
        .withExposedPorts(9092)
        .withNetworkAliases("kafka");
  }

  @Bean
  @ServiceConnection(name = "openzipkin/zipkin")
  GenericContainer<?> zipkinContainer() {
    return new GenericContainer<>(
        DockerImageName.parse("openzipkin/zipkin:3")).withExposedPorts(9411);
  }

}

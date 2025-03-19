package piper1970.notificationservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@SuppressWarnings("all")
class TestNotificationsConfiguration {

  @Bean
  @ServiceConnection
  KafkaContainer kafkaContainer() {
    return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0-rc3")
    ).withNetworkAliases("kafka")
        .withExposedPorts(9092);
  }

  @Bean
  @ServiceConnection(name = "openzipkin/zipkin")
  GenericContainer<?> zipkinContainer() {
    return new GenericContainer<>(
        DockerImageName.parse("openzipkin/zipkin:3")).withExposedPorts(9411);
  }

}

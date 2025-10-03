package piper1970.api_gateway.config;

import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ServerSslConfig {

  /// Configuration customizer for injecting SSL keystore/truststore features
  /// into Netty web server
  @Bean
  public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerFactoryCustomizer(
      SslBundles sslBundles, @Value("${server.ssl.enabled:false}") boolean sslEnabled) {

    return factory -> {
      if (sslEnabled) {
        factory.addServerCustomizers(httpServer -> {
          try {
            var sslBundle = sslBundles.getBundle("event-booking-service");

            // Create ssl context for server, both keystore and truststore enabled
            // expects keystore and truststore manager factories both to be set...
            var sslContext = io.netty.handler.ssl.SslContextBuilder.forServer(
                    sslBundle.getManagers().getKeyManagerFactory())
                .trustManager(sslBundle.getManagers().getTrustManagerFactory()).build();

            return httpServer.secure(spec -> spec.sslContext(sslContext));
          } catch (SSLException e) {
            log.error("Error while creating SSL context for Netty Server. Closing application...", e);
            throw new RuntimeException(e);
          }
        });
      }
    };
  }
}

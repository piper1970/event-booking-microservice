package piper1970.api_gateway.config;

import static io.netty.handler.ssl.SslProvider.JDK;

import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;

//@Configuration
@Slf4j
public class NettyConfig {

//  @Bean
  public NettyServerCustomizer nettyServerCustomizer(SslBundles sslBundles,
      @Value("${server.ssl.enabled:false}") boolean sslEnabled) {

    return server -> {
      if (sslEnabled) {

        try {

          var bundle = sslBundles.getBundle("event-booking-service");
          var managers = bundle.getManagers();

          // Netty's HttpClient requires its own SslContext version.
          var sslContext = io.netty.handler.ssl.SslContextBuilder
              .forClient()
              .sslProvider(JDK)
              .trustManager(managers.getTrustManagerFactory())
              .keyManager(managers.getKeyManagerFactory())
              .build();

          return server.secure(spec -> spec.sslContext(sslContext));

        } catch (SSLException e) {
          log.error("Netty Server SSL Customization failed: {}", e.getMessage());
          throw new RuntimeException(e);
        }
      }
      return server;
    };
  }
}

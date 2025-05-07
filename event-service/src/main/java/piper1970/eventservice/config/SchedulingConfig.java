package piper1970.eventservice.config;

import io.r2dbc.spi.ConnectionFactory;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "${shedlock.lockAtMostFor.default:PT30M}")
public class SchedulingConfig {

  /**
   * Shedlock for locking behavior during scheduled tasks. Setup for use-case with multiple pod instances running concurrently
   * @param connectionFactory Database connection factory for reactional database
   * @return LockProvider instance used to create scheduling locks
   */
  @Bean
  public LockProvider lockProvider(ConnectionFactory connectionFactory) {
    return new R2dbcLockProvider(connectionFactory);
  }

}

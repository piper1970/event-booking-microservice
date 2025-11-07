package piper1970.eventservice.common.validation.validators.context;

import java.util.Optional;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;

/**
 * Custom {@link ApplicationContextAware} component. Used to access spring-managed beans from outside
 * spring context. In this case, for CustomFutureValidator class, which is instantiated via Bean
 * validation mechanism.
 *
 * @see CustomFutureValidator for usage in accessing managed Clock bean
 */
public class ValidationContextProvider implements ApplicationContextAware {

  private static ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
    ValidationContextProvider.applicationContext = applicationContext;
  }

  public static <T> T getBean(Class<T> cls) {
    return Optional.ofNullable(applicationContext)
        .map(ctx -> ctx.getBean(cls))
        .orElseThrow(() ->
            new RuntimeException("ValidationContextProvider::getBean called before applicationContext was initialized"));
  }
}

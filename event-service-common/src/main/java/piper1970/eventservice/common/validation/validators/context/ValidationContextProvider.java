package piper1970.eventservice.common.validation.validators.context;

import java.util.Optional;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

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

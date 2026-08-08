package dev.asyncluna.zenith.core.i18n;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class I18nManager implements Translatable {
  private final MessageSource messageSource;

  @Override
  public String localize(String key, Locale locale, Object... args) {
    try {
      return messageSource.getMessage(key, args, locale);
    } catch (NoSuchMessageException exception) {
      log.error("Missing translation key: '{}' for locale: '{}'", key, locale);
      return key;
    }
  }
}

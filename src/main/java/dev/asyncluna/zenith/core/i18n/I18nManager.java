package dev.asyncluna.zenith.core.i18n;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class I18nManager implements Translatable {
  private static final Pattern ARGUMENT_PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

  private final MessageSource messageSource;

  @Override
  public String localize(String key, Locale locale, Object... args) {
    try {
      // Read the raw message first. MessageFormat treats apostrophes as quote
      // characters, which can both alter the translation and leave numbered
      // placeholders untouched depending on the configured MessageSource.
      String message = messageSource.getMessage(key, null, locale);
      Matcher matcher = ARGUMENT_PLACEHOLDER.matcher(message);
      StringBuffer result = new StringBuffer();

      while (matcher.find()) {
        int argumentIndex = Integer.parseInt(matcher.group(1));
        String replacement =
            argumentIndex < args.length && args[argumentIndex] != null
                ? String.valueOf(args[argumentIndex])
                : matcher.group();
        matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      }

      matcher.appendTail(result);
      return result.toString();
    } catch (NoSuchMessageException exception) {
      log.error("Missing translation key: '{}' for locale: '{}'", key, locale);
      return key;
    }
  }
}

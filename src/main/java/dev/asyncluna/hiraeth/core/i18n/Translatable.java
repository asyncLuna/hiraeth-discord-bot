package dev.asyncluna.hiraeth.core.i18n;

import java.util.Locale;

public interface Translatable {
  String localize(String key, Locale locale, Object... args);
}

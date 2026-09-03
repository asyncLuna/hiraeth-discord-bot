package dev.asyncluna.zenith.core.i18n;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SupportedLocale {
    ENGLISH(Locale.ENGLISH),
    POLISH(Locale.forLanguageTag("pl-PL"));

    private final Locale locale;

    public static SupportedLocale forLanguageTag(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) return SupportedLocale.ENGLISH;

        for (SupportedLocale supportedLocale : values()) {
            if (languageTag.equals(supportedLocale.getLocale().toLanguageTag())) {
                return supportedLocale;
            }
        }

        return SupportedLocale.ENGLISH;
    }
}

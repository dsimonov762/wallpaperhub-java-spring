package dev.wallpaperhub.platform.web;

import java.util.Locale;
import java.util.Set;

/** E4: the language of a response is the first tag of {@code Accept-Language}: en (also the fallback), de or es. */
public final class Language {

    public static final String DEFAULT = "en";
    private static final Set<String> SUPPORTED = Set.of("en", "de", "es");

    private Language() {}

    public static String of(String acceptLanguage) {
        if (acceptLanguage == null) {
            return DEFAULT;
        }
        String tag = acceptLanguage;
        int comma = tag.indexOf(',');
        if (comma >= 0) {
            tag = tag.substring(0, comma);
        }
        int semicolon = tag.indexOf(';');
        if (semicolon >= 0) {
            tag = tag.substring(0, semicolon);
        }
        tag = tag.strip().toLowerCase(Locale.ROOT);
        int dash = tag.indexOf('-');
        if (dash >= 0) {
            tag = tag.substring(0, dash);
        }
        return SUPPORTED.contains(tag) ? tag : DEFAULT;
    }
}

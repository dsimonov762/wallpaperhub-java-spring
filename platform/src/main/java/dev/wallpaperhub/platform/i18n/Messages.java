package dev.wallpaperhub.platform.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * The localization bundles of the template ({@code contracts/i18n}, copied into the jar as {@code i18n/messages.<lang>.json}) and the
 * placeholder syntax of {@code contracts/i18n/README.md}: {@code {name}}, {@code {{} and {@code }}}, one pass, a missing argument
 * leaves the token in the text.
 */
public final class Messages {

    public static final String FALLBACK = "en";
    public static final List<String> LANGUAGES = List.of("en", "de", "es");

    private static final Pattern TOKEN = Pattern.compile("\\{\\{|\\}\\}|\\{([A-Za-z][A-Za-z0-9]*)\\}");

    private final Map<String, Map<String, String>> bundles;

    public Messages(Map<String, Map<String, String>> bundles) {
        this.bundles = bundles;
    }

    /** Reads {@code i18n/messages.<lang>.json} of the class path for en, de and es. */
    public static Messages load(JsonMapper mapper) {
        Map<String, Map<String, String>> bundles = new HashMap<>();
        for (String lang : LANGUAGES) {
            String resource = "/i18n/messages." + lang + ".json";
            try (InputStream in = Messages.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("localization bundle " + resource + " is not on the class path");
                }
                Map<?, ?> raw = mapper.readValue(in, Map.class);
                Map<String, String> bundle = new LinkedHashMap<>();
                raw.forEach((k, v) -> bundle.put(String.valueOf(k), String.valueOf(v)));
                bundles.put(lang, bundle);
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + resource, e);
            }
        }
        return new Messages(bundles);
    }

    /** True when the English bundle (the reference of the other two) has the key. */
    public boolean has(String key) {
        return bundles.getOrDefault(FALLBACK, Map.of()).containsKey(key);
    }

    /** The text of {@code key} in {@code lang}, English when the language or the key is missing there; the key itself when nobody has it (a bug). */
    public String text(String lang, String key, Map<String, ?> args) {
        String template = bundles.getOrDefault(lang, Map.of()).get(key);
        if (template == null) {
            template = bundles.getOrDefault(FALLBACK, Map.of()).get(key);
        }
        if (template == null) {
            return key;
        }
        return format(template, args);
    }

    public static String format(String template, Map<String, ?> args) {
        Matcher m = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group();
            String replacement;
            if ("{{".equals(token)) {
                replacement = "{";
            } else if ("}}".equals(token)) {
                replacement = "}";
            } else if (args.containsKey(m.group(1))) {
                replacement = String.valueOf(args.get(m.group(1)));
            } else {
                replacement = token;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}

package com.uber.notification.application.template;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, dependency-free {{placeholder}} substitution engine. Deliberately avoids pulling in
 * a full template engine (Thymeleaf/FreeMarker) since notification bodies are short, versioned
 * strings rather than full documents — keeping this in `application` (no framework deps) also
 * lets it be unit tested without a Spring context.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> context) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(template, last, matcher.start());
            String key = matcher.group(1);
            String value = context != null ? context.get(key) : null;
            result.append(value != null ? value : "");
            last = matcher.end();
        }
        result.append(template.substring(last));
        return result.toString();
    }
}

package com.uber.notification.application.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void substitutesKnownPlaceholders() {
        String result = TemplateRenderer.render("Hello {{name}}, your order {{orderId}} shipped!",
                Map.of("name", "Alice", "orderId", "A123"));
        assertThat(result).isEqualTo("Hello Alice, your order A123 shipped!");
    }

    @Test
    void leavesUnknownPlaceholdersBlank() {
        String result = TemplateRenderer.render("Hi {{name}}", Map.of());
        assertThat(result).isEqualTo("Hi ");
    }

    @Test
    void returnsNullForNullTemplate() {
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
    }

    @Test
    void handlesTemplateWithNoPlaceholders() {
        assertThat(TemplateRenderer.render("Plain text", Map.of("x", "y"))).isEqualTo("Plain text");
    }

    @Test
    void handlesNullContextGracefully() {
        assertThat(TemplateRenderer.render("Hi {{name}}", null)).isEqualTo("Hi ");
    }
}

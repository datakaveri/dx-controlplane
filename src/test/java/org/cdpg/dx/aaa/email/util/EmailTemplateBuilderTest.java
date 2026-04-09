package org.cdpg.dx.aaa.email.util;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailTemplateBuilderTest {

  @Test
  @DisplayName("substituteVariables replaces a single variable")
  void replacesSingleVariable() {
    String template = "Hello, ${NAME}!";
    Map<String, String> vars = Map.of("NAME", "Alice");

    String result = EmailTemplateBuilder.substituteVariables(template, vars);

    assertThat(result).isEqualTo("Hello, Alice!");
  }

  @Test
  @DisplayName("substituteVariables replaces multiple different variables")
  void replacesMultipleVariables() {
    String template = "Dear ${FIRST_NAME} ${LAST_NAME}, your order #${ORDER_ID} is confirmed.";
    Map<String, String> vars =
        Map.of(
            "FIRST_NAME", "Bob",
            "LAST_NAME", "Smith",
            "ORDER_ID", "12345");

    String result = EmailTemplateBuilder.substituteVariables(template, vars);

    assertThat(result)
        .isEqualTo("Dear Bob Smith, your order #12345 is confirmed.");
  }

  @Test
  @DisplayName("substituteVariables returns template unchanged when no placeholders present")
  void handlesNoVariablesInTemplate() {
    String template = "This template has no variables.";
    Map<String, String> vars = Map.of("KEY", "value");

    String result = EmailTemplateBuilder.substituteVariables(template, vars);

    assertThat(result).isEqualTo("This template has no variables.");
  }

  @Test
  @DisplayName("substituteVariables returns template unchanged with empty map")
  void handlesEmptyMap() {
    String template = "Hello, ${NAME}! Welcome to ${PLATFORM}.";
    Map<String, String> vars = Collections.emptyMap();

    String result = EmailTemplateBuilder.substituteVariables(template, vars);

    assertThat(result).isEqualTo("Hello, ${NAME}! Welcome to ${PLATFORM}.");
  }

  @Test
  @DisplayName("substituteVariables replaces multiple occurrences of the same variable")
  void replacesMultipleOccurrencesOfSameVariable() {
    String template = "${GREETING}, ${GREETING}!";
    Map<String, String> vars = Map.of("GREETING", "Hi");

    String result = EmailTemplateBuilder.substituteVariables(template, vars);

    assertThat(result).isEqualTo("Hi, Hi!");
  }
}

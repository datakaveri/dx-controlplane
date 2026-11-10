package org.cdpg.dx.email.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

public class TemplateCreator {
  private TemplateCreator() {}

  public static String loadTemplateSafe(String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalArgumentException("Template path is null/blank");
    }
    try (InputStream is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new RuntimeException("Template not found on classpath: " + resourcePath);
      }
      try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
        return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
      }
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template: " + resourcePath, e);
    }
  }

  public static String render(String template, Map<String, String> replacements) {
    if (template == null) return "";
    String result = template;
    if (replacements != null && !replacements.isEmpty()) {
      for (Map.Entry<String, String> e : replacements.entrySet()) {
        String key = "${" + e.getKey() + "}";
        String value = e.getValue() == null ? "" : e.getValue();
        result = result.replace(key, value);
      }
    }
    return result;
  }
}

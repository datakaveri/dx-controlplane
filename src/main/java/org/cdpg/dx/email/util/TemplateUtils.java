package org.cdpg.dx.email.util;

import io.vertx.core.json.JsonObject;
import java.io.File;
import java.nio.file.Path;

public class TemplateUtils {
  private TemplateUtils() {}

  public static TemplateRef toTemplateRef(String templateKeyOrValue, JsonObject config) {
    if (templateKeyOrValue == null || templateKeyOrValue.isBlank()) return null;

    String resolved = TemplateSelector.resolveTemplatePath(templateKeyOrValue);
    if (resolved == null || resolved.isBlank()) return null;

    String lower = resolved.toLowerCase().strip();

    if (looksLikeHtml(resolved)) {
      return TemplateRef.inline(resolved);
    }
    /*if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return TemplateRef.url(resolved);
    }*/
    /*if (lower.startsWith("file:")) {
      return TemplateRef.file(resolved.substring(5));
    }
    Path p = Path.of(resolved);
    if (p.isAbsolute() || resolved.contains(File.separator)) {
      // relative filesystem path allowed; TemplateResolver may prepend systemTemplateDir
      return TemplateRef.file(resolved);
    }*/
    // default to classpath resource
    return TemplateRef.resource(resolved);
  }

  private static boolean looksLikeHtml(String s) {
    String low = s.toLowerCase();
    return low.contains("<html")
        || low.contains("<body")
        || low.contains("<div")
        || low.contains("<!doctype");
  }
}

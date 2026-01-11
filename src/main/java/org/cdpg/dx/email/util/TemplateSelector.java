package org.cdpg.dx.email.util;

import java.util.Map;
import org.cdpg.dx.email.model.TemplateType;

public class TemplateSelector {
  private static final Map<String, String> MAP =
      Map.of(
          "create_access_request", TemplateType.ASSET_REQUEST_CREATE.getPath(),
          "approve_access_request", TemplateType.ASSET_REQUEST_APPROVAL.getPath());

  private TemplateSelector() {}

  public static String resolveTemplatePath(String keyOrPath) {
    if (keyOrPath == null || keyOrPath.isBlank()) return null;
    String normalized = keyOrPath.strip();
    // if direct mapping exists, return it
    String mapped = MAP.get(normalized);
    if (mapped != null) return mapped;
    // otherwise return input (could be resource path, file path, html)
    return normalized;
  }
}

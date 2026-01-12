package org.cdpg.dx.email.util;


import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TemplateResolver {
  /*private final JsonObject config;*/
  TemplateRef ref;

  public TemplateResolver(/*JsonObject config*/ TemplateRef ref) {
    /*this.config = config == null ? new JsonObject() : config;*/
    this.ref = ref;
  }

  public String resolve() {
    if (ref == null) {
      throw new IllegalArgumentException("TemplateRef cannot be null");
    }
    switch (ref.getType()) {
      case INLINE:
        return ref.getValue();
      case PATH:
        return loadResource(ref.getValue());
      /*case FILE:
      return loadFile(ref.getValue());*/
      /*case URL:
      return fetchUrl(ref.getValue());*/
      default:
        throw new IllegalArgumentException("Unsupported template type: " + ref.getType());
    }
  }

  private String loadResource(String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalArgumentException("Resource path is null/blank");
    }
    try (InputStream is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new RuntimeException("Resource not found: " + resourcePath);
      }
      try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
        return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
      }
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Failed to load resource template: " + resourcePath, e);
    }
  }

  /*private String loadFile(String pathStr) {
      if (pathStr == null || pathStr.isBlank()) {
          throw new IllegalArgumentException("File path is null/blank");
      }
      try {
          Path path = Path.of(pathStr);
          if (!path.isAbsolute()) {
              String base = config.getString("systemTemplateDir");
              if (base != null && !base.isBlank()) {
                  path = Path.of(base).resolve(pathStr);
              }
          }
          return Files.readString(path, StandardCharsets.UTF_8);
      } catch (Exception e) {
          throw new RuntimeException("Failed to load file template: " + pathStr, e);
      }
  }*/

  private String fetchUrl(String urlStr) {
    if (urlStr == null || urlStr.isBlank()) {
      throw new IllegalArgumentException("URL is null/blank");
    }
    HttpURLConnection conn = null;
    try {
      URL url = new URL(urlStr);
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(3000);
      conn.setReadTimeout(5000);
      try (InputStream is = conn.getInputStream();
          Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
        return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to fetch template from URL: " + urlStr, e);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }
}

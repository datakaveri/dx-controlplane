package org.cdpg.dx.email.util;

public class TemplateRef {
  private final Type type;
  private final String value;

  public TemplateRef(Type type, String value) {
    this.type = type;
    this.value = value;
  }

  public static TemplateRef resource(String resourcePath) {
    return new TemplateRef(Type.PATH, resourcePath);
  }

  /*public static TemplateRef file(String filePath) {
    return new TemplateRef(Type.FILE, filePath);
  }*/

  public static TemplateRef inline(String html) {
    return new TemplateRef(Type.INLINE, html);
  }

  public Type getType() {
    return type;
  }

  public String getValue() {
    return value;
  }

  public enum Type {
    PATH, // classpath resource path
    /*FILE, // filesystem path*/
    INLINE // raw HTML
  }

  /*public static TemplateRef url(String url) {
      return new TemplateRef(Type.URL, url);
  }*/
}

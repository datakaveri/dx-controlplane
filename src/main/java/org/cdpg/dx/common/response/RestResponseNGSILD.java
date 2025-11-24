package org.cdpg.dx.common.response;

import io.vertx.core.json.JsonObject;

public class RestResponseNGSILD {
  private final String type;
  private final String title;
  private final String detail;
  private final String instance;
  private int status;

  private RestResponseNGSILD(String type, String title, String detail, String instance) {
    super();
    this.type = type;
    this.title = title;
    this.detail = detail;
    this.instance = instance;
  }

  private RestResponseNGSILD(
      String type, String title, String detail, int status, String instance) {
    super();
    this.type = type;
    this.title = title;
    this.detail = detail;
    this.status = status;
    this.instance = instance;
  }

  public JsonObject toJson() {
    if (status != 0) {
      return new JsonObject()
          .put("statusCode", status)
          .put("type", this.type)
          .put("title", this.title)
          .put("detail", this.detail)
          .put("instance", this.instance);
    }
    return new JsonObject()
        .put("type", this.type)
        .put("title", this.title)
        .put("detail", this.detail)
        .put("instance", this.instance);
  }

  public static class Builder {
    private String type;
    private String title;
    private String detail;
    private String instance;
    private int status;

    public Builder() {}

    public RestResponseNGSILD.Builder withType(String type) {
      this.type = type;
      return this;
    }

    public RestResponseNGSILD.Builder withTitle(String title) {
      this.title = title;
      return this;
    }

    public RestResponseNGSILD.Builder withMessage(String message) {
      this.detail = message;
      return this;
    }

    public RestResponseNGSILD.Builder withInstance(String instance) {
      this.instance = instance;
      return this;
    }

    public RestResponseNGSILD build() {
      return new RestResponseNGSILD(this.type, this.title, this.detail, this.instance);
    }

    public RestResponseNGSILD build(
        int statusCode, String type, String title, String detail, String instance) {
      this.type = type;
      this.title = title;
      this.detail = detail;
      this.status = statusCode;
      this.instance = instance;
      return new RestResponseNGSILD(this.type, this.title, this.detail, this.status, this.instance);
    }
  }
}

package org.cdpg.dx.common.response;

import io.vertx.core.json.JsonObject;

public class RestResponse {
  private final String type;
  private final String title;
  private final String detail;
  private int status;

  private RestResponse(String type, String title, String detail) {
    super();
    this.type = type;
    this.title = title;
    this.detail = detail;
  }

  private RestResponse(String type, String title, String detail, int status) {
    super();
    this.type = type;
    this.title = title;
    this.detail = detail;
    this.status = status;
  }

  public JsonObject toJson() {
    if (status != 0) {
      return new JsonObject()
          .put("statusCode", status)
          .put("type", this.type)
          .put("title", this.title)
          .put("detail", this.detail);
    }
    return new JsonObject()
        .put("type", this.type)
        .put("title", this.title)
        .put("detail", this.detail);
  }

  public static class Builder {
    private String type;
    private String title;
    private String detail;
    private int status;

    public Builder() {
    }

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    public Builder withTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder withMessage(String message) {
      this.detail = message;
      return this;
    }

    public RestResponse build() {
      return new RestResponse(this.type, this.title, this.detail);
    }

    public RestResponse build(int statusCode, String type, String title, String detail) {
      this.type = type;
      this.title = title;
      this.detail = detail;
      this.status = statusCode;
      return new RestResponse(this.type, this.title, this.detail, this.status);
    }
  }
}

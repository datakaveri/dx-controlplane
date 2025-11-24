package org.cdpg.dx.common.response;

import io.vertx.core.json.JsonObject;

public class DxErrorResponseNGSILD {
  private final String type;
  private final String title;
  private final String detail;
  private final String instance;

  public DxErrorResponseNGSILD(String type, String title, String detail, String instance) {
    this.type = type;
    this.title = title;
    this.detail = detail;
    this.instance = instance;
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("type", type)
        .put("title", title)
        .put("detail", detail)
        .put("instance", instance);
  }
}

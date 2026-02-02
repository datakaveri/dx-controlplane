package org.cdpg.dx.database.elastic.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class BulkScriptUpdate {

  private String id;
  private String scriptSource;
  private JsonObject scriptParams;

  // Mandatory empty constructor (Vert.x)
  public BulkScriptUpdate() {}

  public BulkScriptUpdate(JsonObject json) {
    this.id = json.getString("id");
    this.scriptSource = json.getString("scriptSource");
    this.scriptParams = json.getJsonObject("scriptParams");
  }

  public BulkScriptUpdate(
      String id,
      String scriptSource,
      JsonObject scriptParams
  ) {
    this.id = id;
    this.scriptSource = scriptSource;
    this.scriptParams = scriptParams;
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("id", id)
        .put("scriptSource", scriptSource)
        .put("scriptParams", scriptParams);
  }

  // Getters / setters — required by Vert.x codegen

  public String getId() {
    return id;
  }

  public BulkScriptUpdate setId(String id) {
    this.id = id;
    return this;
  }

  public String getScriptSource() {
    return scriptSource;
  }

  public BulkScriptUpdate setScriptSource(String scriptSource) {
    this.scriptSource = scriptSource;
    return this;
  }

  public JsonObject getScriptParams() {
    return scriptParams;
  }

  public BulkScriptUpdate setScriptParams(JsonObject scriptParams) {
    this.scriptParams = scriptParams;
    return this;
  }
}
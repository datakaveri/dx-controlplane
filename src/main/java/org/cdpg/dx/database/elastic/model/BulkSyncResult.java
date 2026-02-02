package org.cdpg.dx.database.elastic.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.List;

@DataObject(generateConverter = true)
public class BulkSyncResult {

  private int total;
  private int successful;
  private int failed;
  private List<JsonObject> failures;

  public BulkSyncResult() {
  }

  public BulkSyncResult(JsonObject json) {
    this.total = json.getInteger("total", 0);
    this.successful = json.getInteger("successful", 0);
    this.failed = json.getInteger("failed", 0);
    this.failures = json.getJsonArray("failures") != null
        ? json.getJsonArray("failures").getList()
        : List.of();
  }

  public BulkSyncResult(
      int total,
      int successful,
      int failed,
      List<JsonObject> failures
  ) {
    this.total = total;
    this.successful = successful;
    this.failed = failed;
    this.failures = failures;
  }

  public int getTotal() {
    return total;
  }

  public int getSuccessful() {
    return successful;
  }

  public int getFailed() {
    return failed;
  }

  public List<JsonObject> getFailures() {
    return failures;
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("total", total)
        .put("successful", successful)
        .put("failed", failed)
        .put("failures", failures);
  }
}

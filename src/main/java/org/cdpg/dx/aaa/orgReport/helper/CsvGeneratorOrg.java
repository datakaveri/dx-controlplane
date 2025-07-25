package org.cdpg.dx.aaa.orgReport.helper;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import org.cdpg.dx.aaa.organization.models.Organization;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CsvGeneratorOrg {

  public Future<ReadStream<Buffer>> toCsvStream(
          List<Organization> rows, Vertx vertx, boolean writeHeader) {
    if (rows == null || rows.isEmpty()) {
      return Future.succeededFuture(null);
    }

    JsonObject firstRow = rows.get(0).toJson();
    List<String> headers = new ArrayList<>(firstRow.fieldNames());
    List<String> lines = new ArrayList<>();
    if (writeHeader) {
      lines.add(String.join(",", headers));
    }
    for (Organization log : rows) {
      JsonObject row = log.toJson();
      String line =
          headers.stream().map(h -> escapeCsv(row.getValue(h))).collect(Collectors.joining(","));
      lines.add(line);
    }
    ReadStream<Buffer> stream =
        new ReadStream<>() {
          private int idx = 0;
          private Handler<Buffer> dataHandler;
          private Handler<Void> endHandler;
          private Handler<Throwable> exceptionHandler;
          private boolean paused = false;

          @Override
          public ReadStream<Buffer> handler(Handler<Buffer> handler) {
            this.dataHandler = handler;
            if (handler != null && !paused && idx < lines.size()) {
              vertx.runOnContext(v -> pump());
            }
            return this;
          }

          @Override
          public ReadStream<Buffer> pause() {
            paused = true;
            return this;
          }

          @Override
          public ReadStream<Buffer> resume() {
            if (paused) {
              paused = false;
              if (dataHandler != null && idx < lines.size()) {
                vertx.runOnContext(v -> pump());
              }
            }
            return this;
          }

          @Override
          public ReadStream<Buffer> fetch(long amount) {
            return this;
          }

          @Override
          public ReadStream<Buffer> endHandler(Handler<Void> handler) {
            this.endHandler = handler;
            return this;
          }

          @Override
          public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            this.exceptionHandler = handler;
            return this;
          }

          private void pump() {
            while (!paused && idx < lines.size() && dataHandler != null) {
              try {
                dataHandler.handle(Buffer.buffer(lines.get(idx) + "\n"));
              } catch (Exception e) {
                if (exceptionHandler != null) exceptionHandler.handle(e);
                return;
              }
              idx++;
            }
            if (idx >= lines.size() && endHandler != null) {
              endHandler.handle(null);
            }
          }
        };

    return Future.succeededFuture(stream);
  }

  private String escapeCsv(Object value) {
    if (value == null) return "";
    String str = value.toString();
    if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
      str = str.replace("\"", "\"\"");
      return "\"" + str + "\"";
    }
    return str;
  }
}

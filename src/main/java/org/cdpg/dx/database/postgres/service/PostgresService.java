package org.cdpg.dx.database.postgres.service;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import org.cdpg.dx.database.postgres.models.*;

import java.util.List;
import java.util.function.Function;

@VertxGen
@ProxyGen
public interface PostgresService {

  static PostgresService createProxy(Vertx vertx, String address) {
    return new PostgresServiceVertxEBProxy(vertx, address);
  }

  Future<QueryResult> insert(InsertQuery query);

  Future<QueryResult> update(UpdateQuery query);

  Future<QueryResult> delete(DeleteQuery query);

  Future<QueryResult> select(SelectQuery query, boolean isCountQueryEnabled);

  Future<Boolean> ping();

  Future<QueryResult> upsert(UpsertQuery query);

 Future<QueryResult> executeQuery(String sql, JsonArray params);
}

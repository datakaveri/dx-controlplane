package org.cdpg.dx.aaa.grpc.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.apis.catalogue.v1.GetItemRequest;
import org.cdpg.dx.apis.catalogue.v1.GetItemResponse;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemApiServiceTest {

  private static final String ID = "8b95ab80-2aaf-4636-a65e-7f2563d0d371";

  private Vertx vertx;
  private ElasticsearchService es;
  private ItemApiService svc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    es = mock(ElasticsearchService.class);
    svc = new ItemApiService(vertx, es, "items");
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void returnsTheStoredDocument() throws Exception {
    JsonObject source = new JsonObject().put("id", ID).put("name", "rain");
    when(es.getSingleDocument(eq("items"), any()))
        .thenReturn(Future.succeededFuture(new ElasticsearchResponse(ID, source)));

    Recorder<GetItemResponse> r = new Recorder<>();
    svc.getItem(GetItemRequest.newBuilder().setItemId(ID).build(), r);

    assertThat(new JsonObject(r.value().getItemJson())).isEqualTo(source);
  }

  @Test
  void missingItemIsNotFound() throws Exception {
    when(es.getSingleDocument(eq("items"), any()))
        .thenReturn(Future.succeededFuture(new ElasticsearchResponse()));

    Recorder<GetItemResponse> r = new Recorder<>();
    svc.getItem(GetItemRequest.newBuilder().setItemId(ID).build(), r);

    assertThat(r.code()).isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void rejectsANonUuidWithoutQuerying() throws Exception {
    Recorder<GetItemResponse> r = new Recorder<>();
    svc.getItem(GetItemRequest.newBuilder().setItemId("nope").build(), r);

    assertThat(r.code()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    verifyNoInteractions(es);
  }
}

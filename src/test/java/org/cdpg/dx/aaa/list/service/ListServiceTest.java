package org.cdpg.dx.aaa.list.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("ListServiceImpl Tests")
class ListServiceTest {

  @Mock private ElasticsearchService elasticsearchService;

  private ListServiceImpl listService;

  private static final String DOC_INDEX = "test-doc-index";

  @BeforeEach
  void setUp() {
    listService = new ListServiceImpl(elasticsearchService, DOC_INDEX);
  }

  // ---------------------------------------------------------------------------
  // getAvailableFilters
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAvailableFilters")
  class GetAvailableFilters {

    @Test
    @DisplayName("should fail with DxBadRequestException when filter is null")
    void fail_nullFilter(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              null, null, null, null, null, null, null, null, null, null, null, "search");

      Future<ResponseModel> future = listService.getAvailableFilters(requestDTO);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Missing or empty 'filter' array");
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when filter is empty")
    void fail_emptyFilter(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              null,
              null,
              null,
              null,
              Collections.emptyList(),
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      Future<ResponseModel> future = listService.getAvailableFilters(requestDTO);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Missing or empty 'filter' array");
          });
    }

    @Test
    @DisplayName("should return ResponseModel on successful search with results")
    void success_withResults(VertxTestContext ctx) {
      List<String> filters = List.of("type", "tags");
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              null, null, null, null, filters, null, null, null, null, null, null, "search");

      ElasticsearchResponse esResponse =
          new ElasticsearchResponse("doc1", new JsonObject().put("type", "resource"));
      List<ElasticsearchResponse> esResults = List.of(esResponse);

      when(elasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("AGGREGATION_LIST")))
          .thenReturn(Future.succeededFuture(esResults));

      Future<ResponseModel> future = listService.getAvailableFilters(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(elasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("AGGREGATION_LIST"));
          });
    }

    @Test
    @DisplayName("should return ResponseModel on successful search with empty results")
    void success_emptyResults(VertxTestContext ctx) {
      List<String> filters = List.of("type");
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              null, null, null, null, filters, null, null, null, null, null, null, "search");

      when(elasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("AGGREGATION_LIST")))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<ResponseModel> future = listService.getAvailableFilters(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(elasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("AGGREGATION_LIST"));
          });
    }

    @Test
    @DisplayName("should fail when elasticsearch service returns a failure")
    void fail_elasticsearchFailure(VertxTestContext ctx) {
      List<String> filters = List.of("type");
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              null, null, null, null, filters, null, null, null, null, null, null, "search");

      when(elasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("AGGREGATION_LIST")))
          .thenReturn(Future.failedFuture(new RuntimeException("ES connection failed")));

      Future<ResponseModel> future = listService.getAvailableFilters(requestDTO);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("ES connection failed");
          });
    }
  }
}

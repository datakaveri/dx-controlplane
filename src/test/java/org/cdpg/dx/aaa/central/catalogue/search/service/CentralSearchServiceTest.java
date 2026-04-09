package org.cdpg.dx.aaa.central.catalogue.search.service;

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
import org.cdpg.dx.aaa.central.catalogue.search.util.ResponseModel;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.ElasticsearchSearchResult;
import org.cdpg.dx.database.postgres.models.OrderBy;
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
@DisplayName("CentralSearchServiceImpl Tests")
class CentralSearchServiceTest {

  @Mock private ElasticsearchService centralElasticsearchService;

  private CentralSearchServiceImpl centralSearchService;

  private static final String DOC_INDEX = "test-central-doc-index";

  @BeforeEach
  void setUp() {
    centralSearchService = new CentralSearchServiceImpl(centralElasticsearchService, DOC_INDEX);
  }

  // ---------------------------------------------------------------------------
  // postSearch
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("postSearch")
  class PostSearch {

    @Test
    @DisplayName("should return ResponseModel on successful search")
    void success_withResults(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      ElasticsearchResponse esResponse =
          new ElasticsearchResponse("doc1", new JsonObject().put("name", "test-asset"));
      List<ElasticsearchResponse> esResults = List.of(esResponse);
      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(esResults, 1, null);

      when(centralElasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE"));
          });
    }

    @Test
    @DisplayName("should return ResponseModel with empty results")
    void success_emptyResults(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(Collections.emptyList(), 0, null);

      when(centralElasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE"));
          });
    }

    @Test
    @DisplayName("should fail when elasticsearch service returns failure")
    void fail_elasticsearchFailure(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      when(centralElasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE")))
          .thenReturn(Future.failedFuture(new RuntimeException("ES connection failed")));

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("ES connection failed");
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException for unsupported request type")
    void fail_unsupportedRequestType(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "invalidType");

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Failed to process search request");
          });
    }

    @Test
    @DisplayName("should apply sorting when sort list is provided")
    void success_withSorting(VertxTestContext ctx) {
      List<OrderBy> sortList = List.of(new OrderBy("name", OrderBy.Direction.ASC));

      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              sortList,
              "search");

      ElasticsearchResponse esResponse =
          new ElasticsearchResponse("doc1", new JsonObject().put("name", "sorted-asset"));
      List<ElasticsearchResponse> esResults = List.of(esResponse);
      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(esResults, 1, null);

      when(centralElasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE"));
          });
    }

    @Test
    @DisplayName("should handle organisationAssetSearch request type")
    void success_organisationAssetSearch(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              10,
              1,
              null,
              "org-123",
              null,
              "organisationAssetSearch",
              null,
              false);

      ElasticsearchResponse esResponse =
          new ElasticsearchResponse("doc1", new JsonObject().put("name", "org-asset"));
      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(List.of(esResponse), 1, null);

      when(centralElasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE"));
          });
    }

    @Test
    @DisplayName("should handle platformAssetSearch request type")
    void success_platformAssetSearch(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              10, 1, null, null, "PUBLISHED", "platformAssetSearch", null, false);

      ElasticsearchResponse esResponse =
          new ElasticsearchResponse("doc1", new JsonObject().put("name", "platform-asset"));
      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(List.of(esResponse), 1, null);

      when(centralElasticsearchService.search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postSearch(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("SOURCE"));
          });
    }
  }

  // ---------------------------------------------------------------------------
  // postCount
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("postCount")
  class PostCount {

    @Test
    @DisplayName("should return ResponseModel on successful count")
    void success_withResults(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      ElasticsearchResponse esResponse =
          new ElasticsearchResponse("doc1", new JsonObject().put("count", 42));
      List<ElasticsearchResponse> esResults = List.of(esResponse);
      JsonObject aggregations = new JsonObject().put("count", 42);
      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(esResults, 1, aggregations);

      when(centralElasticsearchService.search(
              eq(DOC_INDEX), any(QueryModel.class), eq("COUNT_AGGREGATION")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postCount(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("COUNT_AGGREGATION"));
          });
    }

    @Test
    @DisplayName("should return ResponseModel with empty results")
    void success_emptyResults(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      ElasticsearchSearchResult searchResult = new ElasticsearchSearchResult(Collections.emptyList(), 0, new JsonObject());

      when(centralElasticsearchService.search(
              eq(DOC_INDEX), any(QueryModel.class), eq("COUNT_AGGREGATION")))
          .thenReturn(Future.succeededFuture(searchResult));

      Future<ResponseModel> future = centralSearchService.postCount(requestDTO);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(centralElasticsearchService)
                .search(eq(DOC_INDEX), any(QueryModel.class), eq("COUNT_AGGREGATION"));
          });
    }

    @Test
    @DisplayName("should fail when elasticsearch service returns failure")
    void fail_elasticsearchFailure(VertxTestContext ctx) {
      QueryDecoderRequestDTO requestDTO =
          new QueryDecoderRequestDTO(
              "textSearch_",
              10,
              1,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "search");

      when(centralElasticsearchService.search(
              eq(DOC_INDEX), any(QueryModel.class), eq("COUNT_AGGREGATION")))
          .thenReturn(Future.failedFuture(new RuntimeException("ES count failed")));

      Future<ResponseModel> future = centralSearchService.postCount(requestDTO);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("ES count failed");
          });
    }
  }
}

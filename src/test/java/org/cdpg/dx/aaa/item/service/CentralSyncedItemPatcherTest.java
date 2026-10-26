package org.cdpg.dx.aaa.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CentralSyncedItemPatcherTest {

  private static final String ITEM_ID = "ecb36e44-cdde-11f1-a4a0-9c2dcd6fef4c";

  private ItemService itemService;
  private ItemService centralItemService;
  private PatchItemRequest request;
  private ElasticsearchResponse localBefore;
  private ElasticsearchResponse centralBefore;

  @BeforeEach
  void setUp() {
    itemService = mock(ItemService.class);
    centralItemService = mock(ItemService.class);
    request =
        new PatchItemRequest(
            ITEM_ID,
            "org-1",
            "user-1",
            new JsonObject().put("publishStatus", "ACTIVE").put("dataUploadStatus", true),
            List.of("cos_admin"));

    localBefore = new ElasticsearchResponse();
    localBefore.setSource(new JsonObject().put("id", ITEM_ID));
    centralBefore = new ElasticsearchResponse();
    centralBefore.setSource(
        new JsonObject().put("id", ITEM_ID).put("publishStatus", "PENDING"));
  }

  @Test
  @DisplayName("central disabled: patches only the local catalogue")
  void centralDisabled() {
    when(itemService.patchItem(request)).thenReturn(Future.succeededFuture(localBefore));

    Future<ElasticsearchResponse> result =
        new CentralSyncedItemPatcher(itemService, centralItemService, false).patchItem(request);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isSameAs(localBefore);
    verify(centralItemService, never()).patchItem(any());
  }

  @Test
  @DisplayName("central enabled: patches both central and local catalogues")
  void patchesBoth() {
    when(centralItemService.patchItem(request)).thenReturn(Future.succeededFuture(centralBefore));
    when(itemService.patchItem(request)).thenReturn(Future.succeededFuture(localBefore));

    Future<ElasticsearchResponse> result =
        new CentralSyncedItemPatcher(itemService, centralItemService, true).patchItem(request);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isSameAs(localBefore);
    verify(centralItemService).patchItem(request);
    verify(itemService).patchItem(request);
  }

  @Test
  @DisplayName("central fails twice: local is not patched")
  void centralFailure() {
    when(centralItemService.patchItem(request))
        .thenReturn(Future.failedFuture(new RuntimeException("es down")));

    Future<ElasticsearchResponse> result =
        new CentralSyncedItemPatcher(itemService, centralItemService, true).patchItem(request);

    assertThat(result.failed()).isTrue();
    verify(centralItemService, times(2)).patchItem(request);
    verify(itemService, never()).patchItem(any());
  }

  @Test
  @DisplayName("local fails: central patched fields are reverted to pre-patch values")
  void localFailureRollsBackCentral() {
    RuntimeException localErr = new RuntimeException("local failed");
    when(centralItemService.patchItem(any())).thenReturn(Future.succeededFuture(centralBefore));
    when(itemService.patchItem(request)).thenReturn(Future.failedFuture(localErr));

    Future<ElasticsearchResponse> result =
        new CentralSyncedItemPatcher(itemService, centralItemService, true).patchItem(request);

    assertThat(result.failed()).isTrue();
    assertThat(result.cause()).isSameAs(localErr);
    verify(centralItemService)
        .patchItem(
            argThat(
                r ->
                    r != request
                        && "PENDING".equals(r.getRequestBody().getString("publishStatus"))
                        && r.getRequestBody().containsKey("dataUploadStatus")
                        && r.getRequestBody().getValue("dataUploadStatus") == null));
  }
}

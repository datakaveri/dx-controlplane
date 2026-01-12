package org.cdpg.dx.aaa.delegation;
import io.vertx.core.*;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ItemOwnershipValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ItemOwnershipValidator.class);

  private final ItemService itemService;

  public ItemOwnershipValidator( ItemService itemService) {
    this.itemService = itemService;
  }

  /**
   * Validate ownership of entities based on delegator’s role.
   */

  public Future<Void> validateItemOwnership(
    UUID delegatorId, UUID userId, List<String> itemIds) {

    LOGGER.info("Validating every asset ownership!");

    if (itemIds == null || itemIds.isEmpty()) {
      return Future.failedFuture(
        new DxForbiddenException("No asset IDs provided"));
    }

    String delegatorIdStr=null;
    if(delegatorId!=null)
    {
      delegatorIdStr = delegatorId.toString();
    }
    String userIdStr = userId.toString();

    List<Future> validations = new ArrayList<>();

    if(delegatorIdStr!=null)
    {
      LOGGER.info("checking ownership of item and delegator/primary user");
    for (String itemId : itemIds) {

      GetItemRequest delegatorRequest =
        new GetItemRequest(itemId, delegatorIdStr);

      GetItemRequest userRequest =
        new GetItemRequest(itemId, userIdStr);

      Future<Void> validationFuture =
        itemService
          .getItemWithAccessChecks(delegatorRequest)
          .recover(err ->
            itemService.getItemWithAccessChecks(userRequest))
          .compose(response -> {
            if (response == null) {
              return Future.failedFuture(
                new DxBadRequestException(
                  "Response is empty for item: " + itemId));
            }
            return Future.succeededFuture();
          });

      validations.add(validationFuture);
      }
    }

    return CompositeFuture.all(validations).mapEmpty();
  }



}

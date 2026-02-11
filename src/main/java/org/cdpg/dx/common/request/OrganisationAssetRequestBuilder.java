package org.cdpg.dx.common.request;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.database.elastic.util.Constants.KEYWORD_KEY;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.AccessPolicyRequestDTO;
import org.cdpg.dx.database.elastic.model.OrderBy;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;

public class OrganisationAssetRequestBuilder {
  private static final Logger LOGGER = LogManager.getLogger(OrganisationAssetRequestBuilder.class);
  private final RoutingContext routingContext;
  private final String defaultOrder = "desc";
  private final String defaultSortBy = "itemCreatedAt";
  private final String requestType = "organisationAssetSearch";

  public OrganisationAssetRequestBuilder(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  public static OrganisationAssetRequestBuilder fromRoutingContext(RoutingContext routingContext) {
    return new OrganisationAssetRequestBuilder(routingContext);
  }

  public QueryDecoderRequestDTO build() {
    MultiMap params = routingContext.queryParams();
    boolean filterMyAssets = Boolean.parseBoolean(params.get(FILTER_MYASSETS));
    return new QueryDecoderRequestDTO(
        getSize(params),
        getPage(params),
        extractSortOrders(),
        getOrgId(routingContext),
        getPublishStatus(params),
        requestType,
        getAccessPolicyRequest(false, getSub(routingContext)),
        filterMyAssets);
  }

  private String getPublishStatus(MultiMap params) {
    return params.get(PUBLISH_STATUS);
  }

  public int getSize(MultiMap params) {
    return params.get(SIZE_KEY) != null ? Integer.parseInt(params.get(SIZE_KEY)) : 100;
  }

  public int getPage(MultiMap params) {
    return params.get(PAGE_KEY) != null ? Integer.parseInt(params.get(PAGE_KEY)) : 1;
  }

  private List<OrderBy> extractSortOrders() {
    List<OrderBy> orderByList = new ArrayList<>();
    MultiMap params = routingContext.request().params(true);
    if (params.get("sort") == null) {
      LOGGER.debug("No sort parameter found in request.");
      return null;
    }
    String sortParam = params.get("sort");
    final int MAX_SORT_FIELDS = 3;

    if (sortParam != null && !sortParam.isEmpty()) {
      String[] items = sortParam.split(";");
      if (items.length > MAX_SORT_FIELDS) {
        throw new DxBadRequestException("Too many sort fields. Max allowed is " + MAX_SORT_FIELDS);
      }

      for (String item : items) {
        String[] parts = item.split(":");
        if (parts.length != 2) {
          throw new DxBadRequestException(
              "Invalid sort format: " + item + ". Expected field:order");
        }

        String field = parts[0].trim();
        String direction = parts[1].trim().toLowerCase();
        if (!field.endsWith(KEYWORD_KEY) && (!field.equalsIgnoreCase("itemCreatedAt"))) {
          field = field + KEYWORD_KEY;
        }
        if (!direction.equals("asc") && !direction.equals("desc")) {
          throw new DxBadRequestException("Invalid sort order: " + direction);
        }

        orderByList.add(new OrderBy(field, OrderBy.Direction.valueOf(direction.toUpperCase())));
      }
    } else if (defaultSortBy != null) {
      orderByList.add(
          new OrderBy(defaultSortBy, OrderBy.Direction.valueOf(defaultOrder.toUpperCase())));
    }

    return orderByList;
  }

  private AccessPolicyRequestDTO getAccessPolicyRequest(boolean isAssetSearch, String sub) {
    return new AccessPolicyRequestDTO(sub, isAssetSearch);
  }
  private String getSub(RoutingContext ctx) {
    try {
      if (ctx.user() != null) {
        return ctx.user().subject();
      }
    } catch (Exception e) {
      throw new DxBadRequestException("User subject not found in context", e);
    }
    return null;
  }
  private String getOrgId(RoutingContext ctx) {
    try {
      if (ctx.user() != null) {
        return ctx.user().principal().getString("organisation_id");
      }
    } catch (Exception e) {
      throw new DxBadRequestException("User not found in context", e);
    }
    return null;
  }
}

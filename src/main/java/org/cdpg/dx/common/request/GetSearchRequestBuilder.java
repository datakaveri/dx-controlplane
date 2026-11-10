package org.cdpg.dx.common.request;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.*;
import org.cdpg.dx.database.postgres.models.OrderBy;
public class GetSearchRequestBuilder {
  private static final Logger LOGGER = LogManager.getLogger(GetSearchRequestBuilder.class);
  private final RoutingContext routingContext;
  private boolean isAssetSearch;
  private String defaultSortBy = "itemCreatedAt";
  private String defaultOrder = "desc";
  private String requestType = "search";

  public GetSearchRequestBuilder(RoutingContext ctx) {
    this.routingContext = ctx;
  }

  public static GetSearchRequestBuilder fromRoutingContext(RoutingContext ctx) {
    return new GetSearchRequestBuilder(ctx);
  }

  public GetSearchRequestBuilder setAssetSearch(boolean assetSearch) {
    this.isAssetSearch = assetSearch;
    return this;
  }

  public QueryDecoderRequestDTO build() {
    MultiMap params = routingContext.queryParams();
    String sub = routingContext.user() != null ? routingContext.user().subject() : null;

    return new QueryDecoderRequestDTO(
        buildSearchType(params),
        getSize(params),
        getPage(params),
        null, // id (GET won’t send body)
        List.of(), // filters (unless want to parse from query)
        null, // text search (unless parse q= from query)
        null,
        new AccessPolicyRequestDTO(sub, isAssetSearch),
        new InstanceFilterRequestDTO(params.get(INSTANCE)),
        null,
        extractSortOrders(),
        requestType);
  }

  private String buildSearchType(MultiMap params) {
    boolean hasFilter = false;
    StringBuilder typeBuilder = new StringBuilder();

    if (isAssetSearch) {
      typeBuilder.append(SEARCH_TYPE_MY_ASSETS_ALL);
      hasFilter = true;
    }
    if (params.contains(FILTER)
        && !params.get(FILTER).isEmpty()) {
      typeBuilder.append(RESPONSE_FILTER);
      hasFilter = true;
    }
    if (!hasFilter) {
      throw new DxBadRequestException("Mandatory field(s) not provided");
    }
    return typeBuilder.toString();
  }
  private int getSize(MultiMap params) {
    return params.get(SIZE_KEY) != null ? Integer.parseInt(params.get(SIZE_KEY)) : 100;
  }

  private int getPage(MultiMap params) {
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
}


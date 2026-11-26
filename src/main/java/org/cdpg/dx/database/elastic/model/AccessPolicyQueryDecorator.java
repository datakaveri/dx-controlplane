package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.aaa.common.Constants.PII;
import static org.cdpg.dx.database.elastic.util.Constants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.util.AssetAccessScopes;
import org.cdpg.dx.database.elastic.util.*;

public class AccessPolicyQueryDecorator implements ElasticsearchQueryDecorator {
  private static final Logger LOGGER = LogManager.getLogger(AccessPolicyQueryDecorator.class);
  private final Map<FilterType, List<QueryModel>> queryMap;
  private final AccessPolicyRequestDTO request;
  private final List<String> callerScopes;
  private final String callerOrganisationId;

  public AccessPolicyQueryDecorator(
      Map<FilterType, List<QueryModel>> queryMap, AccessPolicyRequestDTO request) {
    this(queryMap, request, null, null);
  }

  /**
   * @param callerScopes scopes of the authenticated caller; {@code asset-management} makes every
   *     PRIVATE item visible, {@code org-asset-management} the PRIVATE items of {@code
   *     callerOrganisationId}
   * @param callerOrganisationId organisation of the caller (used with org-asset-management)
   */
  public AccessPolicyQueryDecorator(
      Map<FilterType, List<QueryModel>> queryMap,
      AccessPolicyRequestDTO request,
      List<String> callerScopes,
      String callerOrganisationId) {
    this.queryMap = queryMap;
    this.request = request;
    this.callerScopes = callerScopes;
    this.callerOrganisationId = callerOrganisationId;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    LOGGER.info("Adding access policy query decorator DTO {}", request);
    String sub = request != null ? request.getSub() : null;
    boolean isMyAssetsRequest = request != null && Boolean.TRUE.equals(request.getMyAssetsReq());

    if (sub != null && !sub.isEmpty()) {
      if (isMyAssetsRequest) {
        // Strictly match owned items only
        QueryModel ownerMatch =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, PROVIDER_USER_ID, VALUE, sub));
        queryMap.get(FilterType.MUST).add(ownerMatch);
      } else {
        // User is authenticated → allow: PUBLIC, RESTRICTED, PII, PRIVATE owned,
        // and PRIVATE items the caller administers (asset admins need to review them)
        QueryModel publicAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, OPEN));
        QueryModel restrictedAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, RESTRICTED));
        QueryModel piiAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PII));
        QueryModel privateAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PRIVATE));
        QueryModel ownerMatch =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, PROVIDER_USER_ID, VALUE, sub));
        QueryModel privateOwned =
            new QueryModel(QueryType.BOOL).setMustQueries(List.of(privateAccess, ownerMatch));

        List<QueryModel> shouldQueries =
            new ArrayList<>(List.of(
                publicAccess,
                restrictedAccess,
                privateOwned,
                piiAccess));
        QueryModel privateAdministered = buildPrivateAdministeredQuery(privateAccess);
        if (privateAdministered != null) {
          shouldQueries.add(privateAdministered);
        }
        List<String> sharedItemIds = request.getSharedItemIds();

        if (sharedItemIds != null && !sharedItemIds.isEmpty()) {

          QueryModel sharedItemsQuery =
              new QueryModel(QueryType.TERMS)
                  .setQueryParameters(
                      Map.of(
                          FIELD, ID_KEYWORD,
                          VALUE, sharedItemIds));

          shouldQueries.add(sharedItemsQuery);
        }

        QueryModel accessFilter = new QueryModel(QueryType.BOOL);
        accessFilter.setShouldQueries(shouldQueries);
        accessFilter.setMinimumShouldMatch("1");

        queryMap.get(FilterType.MUST).add(accessFilter);
      }
    } else {
      QueryModel excludePrivate =
          new QueryModel(QueryType.MATCH)
              .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PRIVATE));
      queryMap.get(FilterType.MUST_NOT).add(excludePrivate);
    }
    return queryMap;
  }

  /**
   * PRIVATE items visible to the caller by virtue of an admin scope: all of them for {@code
   * asset-management}, those of the caller's organisation for {@code org-asset-management}.
   * Returns null when the caller holds neither.
   */
  private QueryModel buildPrivateAdministeredQuery(QueryModel privateAccess) {
    if (AssetAccessScopes.hasPlatformAssetScope(callerScopes)) {
      LOGGER.debug("asset-management scope: including all PRIVATE items");
      return privateAccess;
    }
    if (AssetAccessScopes.hasOrgAssetScope(callerScopes)
        && callerOrganisationId != null
        && !callerOrganisationId.isBlank()) {
      LOGGER.debug(
          "org-asset-management scope: including PRIVATE items of organisation {}",
          callerOrganisationId);
      QueryModel orgMatch =
          new QueryModel(QueryType.MATCH)
              .setQueryParameters(
                  Map.of(FIELD, ORGANIZATION_ID_KEYWORD, VALUE, callerOrganisationId));
      return new QueryModel(QueryType.BOOL).setMustQueries(List.of(privateAccess, orgMatch));
    }
    return null;
  }
}

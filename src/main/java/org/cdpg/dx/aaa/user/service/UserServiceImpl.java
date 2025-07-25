package org.cdpg.dx.aaa.user.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private final KeycloakUserService keycloakUserService;
    private final OrganizationService organizationService;
    private final CreditService creditService;

    public UserServiceImpl(KeycloakUserService keycloakUserService, OrganizationService organizationService, CreditService creditService) {

        this.keycloakUserService = keycloakUserService;
        this.creditService = creditService;
        this.organizationService = organizationService;
    }

    @Override
    public Future<DxUser> getUserInfo(DxUser dxUser) {

        Future<Boolean> pendingProvider = Future.succeededFuture(false);



        UUID orgId = null;
        try {
            orgId = UUID.fromString(dxUser.organisationId());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Handle invalid UUID
            LOGGER.error("Invalid UUID for organisationId: {}", dxUser.organisationId());

        }

        if (orgId != null) {
            pendingProvider = organizationService
                    .hasPendingProviderRole(dxUser.sub(), orgId);
        }

        Future<Boolean> pendingCompute = creditService.hasPendingComputeRequest(dxUser.sub());

        Future<List<OrganizationJoinRequest>> joinRequests = organizationService.getOrganizationJoinRequestsByUser(dxUser.sub());

        Future<List<OrganizationCreateRequest>> createRequests = organizationService.getOrganizationCreateRequestsByUserId(dxUser.sub());

        return Future.all(pendingProvider, pendingCompute, joinRequests, createRequests)
                .map(cf -> {
                    List<String> pendingRoles = new ArrayList<>();
                    if (cf.resultAt(0)) pendingRoles.add("provider");
                    if (cf.resultAt(1)) pendingRoles.add("compute");
                    List<OrganizationJoinRequest> joinReqList = cf.resultAt(2);

                    //TODO will consider no only one request will be available need to upda for multiple requests

                    List<OrganizationCreateRequest> createOrgReqList = cf.resultAt(3);
                    JsonObject orgJson = getOrgEntry(createOrgReqList, joinReqList);


                    return DxUser.withPendingRoles(dxUser, pendingRoles, orgJson);
                });
    }

    private static JsonObject getOrgEntry(List<OrganizationCreateRequest> lastOrgCreateReq, List<OrganizationJoinRequest> joinReqList) {

        JsonObject orgJson = new JsonObject();
        if (lastOrgCreateReq != null && !lastOrgCreateReq.isEmpty()) {
            orgJson = lastOrgCreateReq.getFirst().toJsonForUsers();

            orgJson.put("request_type", "organisation_create");
        } else if (joinReqList != null && !joinReqList.isEmpty()) {

            orgJson = joinReqList.getFirst().toJsonForUsers();
            orgJson.put("request_type", "organisation_join");
        }
        LOGGER.debug(orgJson.encodePrettily());
        return orgJson;
    }

    @Override
    public Future<DxUser> getUserInfoByID(UUID userId) {
        return keycloakUserService.getUserById(userId)
                .compose(this::getUserInfo);
    }

}

package org.cdpg.dx.aaa.conversation.service.impl;

import io.vertx.core.Future;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.conversation.model.ConversationParticipants;
import org.cdpg.dx.aaa.conversation.service.ConversationParticipantService;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;

public class ConversationParticipantServiceImpl implements ConversationParticipantService {

  private static final Logger LOGGER =
      LogManager.getLogger(ConversationParticipantServiceImpl.class);

  private final OrganizationService organizationService;
  private final DelegationService delegationService;
  private final UserService userService;

  public ConversationParticipantServiceImpl(
      OrganizationService organizationService, DelegationService delegationService, UserService userService) {

    this.organizationService = organizationService;
    this.delegationService = delegationService;
    this.userService = userService;
  }

  @Override
  public Future<ConversationParticipants> getParticipants(String requestType, UUID requestTypeId) {

    if (requestType == null || requestTypeId == null) {
      return Future.failedFuture(
          new IllegalArgumentException("requestType and requestTypeId are required"));
    }

    return switch (requestType) {
      case "join_orgs" -> getJoinOrganisationParticipants(requestTypeId);

      case "create_orgs" -> getCreateOrganisationParticipants(requestTypeId);
      case "delegation" -> getDelegationParticipants(requestTypeId);

      default ->
          Future.failedFuture(
              new IllegalArgumentException(
                  "Unsupported conversation request type: " + requestType));
    };
  }

  private Future<ConversationParticipants> getDelegationParticipants(UUID delegationId) {

    return delegationService
        .getDelegationGrantById(delegationId.toString())
        .compose(grant -> {

          if (grant == null) {
            return Future.failedFuture(
                new DxNotFoundException(
                    "Delegation grant not found with id: " + delegationId));
          }

          UUID delegatorId = UUID.fromString(grant.getString("delegatorId"));
          UUID delegateId = UUID.fromString(grant.getString("delegateId"));

          return Future.all(
                  userService.getUserInfoByID(delegatorId),
                  userService.getUserInfoByID(delegateId))
              .map(
                  result ->
                      new ConversationParticipants(
                          result.resultAt(0),
                          result.resultAt(1)));
        });
  }

  private Future<ConversationParticipants> getJoinOrganisationParticipants(UUID requestId) {

    return organizationService
        .getOrganizationJoinRequestById(requestId)
        .compose(
            request -> {
              UUID requesterId = request.userId();
              UUID orgId = request.organizationId();

              return organizationService
                  .getUserOrgAdminId(orgId)
                  .compose(
                      approverId ->
                          Future.all(
                              userService.getUserInfoByID(requesterId),
                              userService.getUserInfoByID(approverId)))
                  .map(
                      composite -> {
                        DxUser requester = composite.resultAt(0);
                        DxUser approver = composite.resultAt(1);

                        return new ConversationParticipants(requester, approver);
                      });
            });
  }

  private Future<ConversationParticipants> getCreateOrganisationParticipants(UUID requestId) {

    return organizationService
        .getOrganizationCreateRequestById(requestId)
        .compose(
            request -> {
              UUID requesterId = request.requestedBy();

              // Resolve the approver ID using actual create-org
              // approval mapping.
              UUID approverId = /* resolve approver */ null;

              return Future.all(
                      userService.getUserInfoByID(requesterId),
                      userService.getUserInfoByID(approverId))
                  .map(
                      result ->
                          new ConversationParticipants(result.resultAt(0), result.resultAt(1)));
            });
  }
}

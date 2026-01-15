package org.cdpg.dx.aaa.delegation;
import io.vertx.core.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.cdpg.dx.aaa.delegation.util.Constants.DELEGATOR_ID;


import static io.vertx.core.Future.succeededFuture;

public class OrgOwnershipValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrgOwnershipValidator.class);

  private final OrganizationService organizationService;

  public OrgOwnershipValidator(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  public Future<Void> validateOrgOwnership(UUID delegatorId, List<String> orgIds) {
    LOGGER.info("Validating org Ids ownership");
    LOGGER.info("orgids: {}",orgIds);

    if (orgIds == null || orgIds.isEmpty()) {
      return Future.failedFuture(new DxForbiddenException("No organization ID provided"));
    }

    UUID orgId = UUID.fromString(orgIds.get(0));

    return organizationService.getOrganisationAdminId(orgId)
      .compose(res -> {
        UUID adminId = res.getFirst().userId();
        LOGGER.info("admin: {}",adminId);
        LOGGER.info("delegator: {}",delegatorId);
        if (adminId.equals(delegatorId)) {
          return succeededFuture();
        } else {
          return Future.failedFuture(new DxForbiddenException("Delegator is not an org admin of this organization"));
        }
      });
  }

}

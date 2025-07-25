package org.cdpg.dx.aaa.organization.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OrganizationUserDAO extends BaseDAO<OrganizationUser> {

    Future<Boolean> deleteUserByOrgId(UUID orgId, UUID userID);

    Future<Boolean> isOrgAdmin(UUID orgid, UUID userid);



}

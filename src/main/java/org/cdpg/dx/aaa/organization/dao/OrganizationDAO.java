package org.cdpg.dx.aaa.organization.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

import java.util.List;
import java.util.UUID;

public interface OrganizationDAO extends BaseDAO<Organization> {

}

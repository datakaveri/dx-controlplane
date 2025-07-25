package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

import java.util.List;
import java.util.UUID;

public interface ComputeRoleDAO extends BaseDAO<ComputeRole> {

    Future<Boolean> hasUserComputeAccess(UUID userId);
}

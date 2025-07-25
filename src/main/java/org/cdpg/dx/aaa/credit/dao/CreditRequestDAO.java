package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditRequestDTO;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

import java.util.List;
import java.util.UUID;

public interface CreditRequestDAO extends BaseDAO<CreditRequest> {
//    Future<CreditRequest> create(CreditRequest creditRequest);
//
//    Future<List<CreditRequest>> getAll(Status status);
//
//    Future<Boolean> updateStatus(UUID requestId, Status status);
//
//    Future<CreditRequest> getCreditRequestById(UUID requestId);
}

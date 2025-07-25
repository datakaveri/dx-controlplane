package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

import java.util.UUID;

public interface CreditTransactionDAO extends BaseDAO<CreditTransaction> {

  //Future<Boolean> logTransaction(CreditTransaction creditTransaction);
}

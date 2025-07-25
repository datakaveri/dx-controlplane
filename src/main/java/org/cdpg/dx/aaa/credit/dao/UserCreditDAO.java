package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

import java.util.UUID;

public interface UserCreditDAO extends BaseDAO<UserCredit> {
//
//  Future<Double> getBalance(UUID userId);
//
//  Future<Boolean> updateBalance(UUID userId, double updatedAmount);
}

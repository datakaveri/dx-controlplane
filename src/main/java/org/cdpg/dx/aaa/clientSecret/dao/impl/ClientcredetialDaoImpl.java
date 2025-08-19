package org.cdpg.dx.aaa.clientSecret.dao.impl;

import io.vertx.core.Future;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.model.ClientCredentials;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ClientcredetialDaoImpl extends AbstractBaseDAO<ClientCredentials>
    implements ClientcredetialDao {

  private static final Logger LOGGER = LogManager.getLogger(ClientcredetialDaoImpl.class);

  private static final String COLUMN_CLIENT_ID = "client_id";
  private static final String COLUMN_CLIENT_SECRET = "client_secret";
  private static final String COLUMN_USER_ID = "user_id";
  private static final String CLIENT_CREDENTIALS = "client_credentials";

  public ClientcredetialDaoImpl(PostgresService postgresService) {
    super(postgresService, CLIENT_CREDENTIALS, COLUMN_USER_ID, ClientCredentials::fromJson);
  }

  @Override
  public Future<ClientCredentials> saveClientCredentials(ClientCredentials clientCredentials) {
    return create(clientCredentials);
  }

  @Override
  public Future<String> getClientCredentialsByClientId(String clientId, String clientSecret) {
    Condition finalCondition =
        new Condition(
            List.of(
                new Condition(COLUMN_CLIENT_ID, Condition.Operator.EQUALS, List.of(clientId)),
                new Condition(
                    COLUMN_CLIENT_SECRET, Condition.Operator.EQUALS, List.of(clientSecret))),
            Condition.LogicalOperator.AND);

    SelectQuery selectQuery =
        new SelectQuery(tableName, List.of(COLUMN_USER_ID), finalCondition, null, null, null, null);

    return postgresService
        .select(selectQuery, false)
        .compose(
            result -> {
              if (result.getRows().isEmpty()) {
                String msg = String.format("No client credentials found for clientId=%s", clientId);
                return Future.failedFuture(msg);
              }
              return Future.succeededFuture(
                  result.getRows().getJsonObject(0).getString(COLUMN_USER_ID));
            })
        .recover(
            err -> {
              LOGGER.error(
                  "Error fetching client credentials for clientId={}: {}",
                  clientId,
                  err.getMessage(),
                  err);
              return Future.failedFuture(BaseDxException.from(err));
            });
  }
}

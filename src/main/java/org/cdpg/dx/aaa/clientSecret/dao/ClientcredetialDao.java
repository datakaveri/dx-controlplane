package org.cdpg.dx.aaa.clientSecret.dao;

import io.vertx.core.Future;
import java.util.List;
import org.cdpg.dx.aaa.clientSecret.model.ClientCredentials;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface ClientcredetialDao extends BaseDAO<ClientCredentials> {

  Future<ClientCredentials> saveClientCredentials(ClientCredentials clientCredentials);

  Future<String> getClientCredentialsByClientId(String clientId, String clientSecret);

  Future<ClientCredentials> upsertClientCredentials(
      ClientCredentials clientCredentials,
      List<String> conflictColumns,
      List<String> updateColumns);
}

package org.cdpg.dx.aaa.asset.dao.impl;

import org.cdpg.dx.aaa.asset.dao.AssetRequestDAO;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.util.Constants;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.asset.util.Constants.ASSET_REQUEST_ID;

public class AssetRequestDAOImpl extends AbstractBaseDAO<AssetRequest>  implements AssetRequestDAO {

  public AssetRequestDAOImpl(PostgresService postgresService)
  {
    super(postgresService, Constants.ASSET_REQUEST_TABLE, ASSET_REQUEST_ID, AssetRequest::fromJson);
  }
}

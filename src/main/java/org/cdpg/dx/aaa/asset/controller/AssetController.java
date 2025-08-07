package org.cdpg.dx.aaa.asset.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.asset.handler.AssetHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class AssetController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AssetController.class);

  private final AssetHandler assetHandler;
  private final AuditingHandler auditingHandler;

  public AssetController(AssetHandler assetHandler, AuditingHandler auditingHandler) {
    this.assetHandler = assetHandler;
    this.auditingHandler = auditingHandler;
   }

   @Override
   public void register(RouterBuilder routerBuilder)
   {
    routerBuilder
      .operation("post-auth-v1-asset-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.PROVIDER))
      .handler(assetHandler::createAssetRequest);

    routerBuilder
      .operation("get-auth-v1-asset-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
      .handler(assetHandler::getAllAssetRequests);

    routerBuilder
      .operation("put-auth-v1-asset-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
      .handler(assetHandler::updateAssetRequestStatus);

   }

}

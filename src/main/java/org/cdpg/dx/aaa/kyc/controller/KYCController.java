package org.cdpg.dx.aaa.kyc.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.apiserver.ApiController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;

import java.util.Set;


public class KYCController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(KYCController.class);
  private final KYCHandler kycHandler;
  private final AuditingHandler auditingHandler;

  public KYCController(KYCHandler  kycHandler,AuditingHandler auditingHandler) {
    this.kycHandler = kycHandler;
    this.auditingHandler = auditingHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    routerBuilder
      .operation("get-auth-v2-kyc-confirm")
      .handler(auditingHandler::handleApiAudit)
      .handler(kycHandler::confirmKYC);


    routerBuilder
      .operation("post-auth-v2-kyc-verify")
      .handler(auditingHandler::handleApiAudit)
      .handler(kycHandler::verifyKYC);

    routerBuilder
      .operation("post-auth-v2-kyc-revoke")
      .handler(auditingHandler::handleApiAudit)
      .handler(kycHandler::revokeKYC);

  }


}

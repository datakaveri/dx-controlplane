package org.cdpg.dx.aaa.kyc.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.aaa.apiserver.ApiController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;

import java.util.Set;


public class KYCController implements ApiController {
    private static final Logger LOGGER = LogManager.getLogger(KYCController.class);
    private final KYCHandler kycHandler;

    public KYCController(KYCHandler  kycHandler) {
        this.kycHandler = kycHandler;
    }

    @Override
    public void register(RouterBuilder routerBuilder) {

        routerBuilder
                .operation("get-auth-v1-kyc-confirm")
                .handler(kycHandler::confirmKYC);


        routerBuilder
                .operation("post-auth-v1-kyc-verify")
                .handler(kycHandler::verifyKYC);

        routerBuilder
                .operation("post-auth-v1-kyc-revoke")
                .handler(kycHandler::revokeKYC);

    }


}

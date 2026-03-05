package org.cdpg.dx.aaa.appCredentials.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentialResponse;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.UUID;

public interface AppCredentialsService {

  Future<AppCredentials> createApp(JsonObject appConstraints);

  Future<PaginatedResult<AppCredentialResponse>> getApp(PaginatedRequest paginatedRequest);

  Future<Boolean> deleteApp(UUID userId, UUID appId);

  Future<Boolean> changeAppStatus(UUID userId, UUID appId,String status);

  Future<AppCredentials> getAppById(UUID appId);

  Future<List<AppConstraints>> getAppConstraintsById(UUID appId);

}

package org.cdpg.dx.aaa.appCredentials.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.UUID;

public interface AppCredentialsService {

  Future<AppCredentials> createApp(AppCredentials appCredentials);

  Future<PaginatedResult<AppCredentials>> getApp(PaginatedRequest paginatedRequest);

  Future<Boolean> deleteApp(UUID userId, UUID appId);
}

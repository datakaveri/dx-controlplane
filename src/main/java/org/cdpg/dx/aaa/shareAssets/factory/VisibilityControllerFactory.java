package org.cdpg.dx.aaa.shareAssets.factory;

import org.cdpg.dx.aaa.shareAssets.controller.VisibilityController;
import org.cdpg.dx.aaa.shareAssets.dao.VisibilityDao;
import org.cdpg.dx.aaa.shareAssets.dao.impl.VisibilityDaoImpl;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.aaa.shareAssets.service.impl.VisibilityServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class VisibilityControllerFactory {

  public static VisibilityController createController(
      PostgresService postgresService,
      ElasticsearchService elasticsearchService,
      String docIndex,
      URNGenerator unrGenerator) {
    VisibilityDao visibilityDao = new VisibilityDaoImpl(postgresService);
    VisibilityService visibilityService = new VisibilityServiceImpl(visibilityDao);
    return new VisibilityController(
        visibilityService, elasticsearchService, docIndex, unrGenerator);
  }
}

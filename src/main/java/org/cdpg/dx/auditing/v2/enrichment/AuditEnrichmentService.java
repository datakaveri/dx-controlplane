package org.cdpg.dx.auditing.v2.enrichment;

import io.vertx.core.Future;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;

public class AuditEnrichmentService {

  private final AssetEnrichmentService assetEnrichmentService;
  private final UserEnrichmentService userEnrichmentService;

  public AuditEnrichmentService(
      AssetEnrichmentService assetEnrichmentService, UserEnrichmentService userEnrichmentService) {
    this.assetEnrichmentService = assetEnrichmentService;
    this.userEnrichmentService = userEnrichmentService;
  }

  public Future<ActivityAuditLogEntity> enrich(ActivityAuditLogEntity entity) {

    return assetEnrichmentService.enrich(entity).compose(userEnrichmentService::enrich);
  }
}

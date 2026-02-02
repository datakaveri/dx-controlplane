package org.cdpg.dx.aaa.ActivityReport.model;

import java.util.List;

public record ReportMetaData(List<ActivityAuditLogEntity> activityLogList, long count) {}

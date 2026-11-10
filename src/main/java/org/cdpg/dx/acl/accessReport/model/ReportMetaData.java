package org.cdpg.dx.acl.accessReport.model;

import java.util.List;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;

public record ReportMetaData(List<AccessRequestDto> activityLogList, long count) {}

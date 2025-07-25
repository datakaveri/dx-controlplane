package org.cdpg.dx.aaa.accessReport.model;

import java.util.List;
import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;

public record ReportMetaData(List<AccessRequestDto> activityLogList, long count) {}

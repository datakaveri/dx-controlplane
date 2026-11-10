package org.cdpg.dx.aaa.summary.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.summary.model.UsageSummary;

import java.util.List;

public interface SummaryService {

   Future<List<UsageSummary>> getUsageSummaryForMainDashboard();
}

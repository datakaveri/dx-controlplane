package org.cdpg.dx.aaa.summary.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.summary.model.UsageSummary;

import java.util.List;

public interface UsageSummaryDao {

  /** Fetch all usage summary records from usage_summary table */
  Future<List<UsageSummary>> fetchAllUsageSummaries();
}

package org.cdpg.dx.database.elastic.model;

import java.util.List;
import java.util.Map;

public interface ElasticsearchQueryDecorator {
  Map<FilterType, List<QueryModel>> add();
}

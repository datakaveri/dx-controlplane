package org.cdpg.dx.database.postgres.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.request.TemporalRequest;
import org.cdpg.dx.database.postgres.models.Condition;

public class ConditionBuilder {
  private static final Logger LOGGER = LogManager.getLogger(ConditionBuilder.class);

  public static Condition fromFilters(
          Map<String, ?> filters, List<TemporalRequest> temporalRequests) {
    List<Condition> conditions = new ArrayList<>();

    LOGGER.info("Building conditions from filters: {}", filters);
    LOGGER.info("Building conditions from temporal requests: {}", temporalRequests);
    // Add filters
    if (filters != null) {
      conditions.addAll(
              filters.entrySet().stream()
                      .map(
                              e -> {
                                String key = e.getKey();
                                Object value = e.getValue();

                                if (value instanceof List<?> listVal) {
                                  if (listVal.isEmpty()) {
                                    return null; // skip empty lists
                                  } else if (listVal.size() == 1) {
                                    return new Condition(
                                            key, Condition.Operator.EQUALS, List.of(listVal.get(0)));
                                  } else {
                                    return new Condition(key, Condition.Operator.IN, new ArrayList<>(listVal));
                                  }
                                } else if (value != null) {
                                  return new Condition(key, Condition.Operator.EQUALS, List.of(value));
                                } else {
                                  return null;
                                }
                              })
                      .filter(c -> c != null)
                      .toList());
    }

    // Add temporal conditions
    if (temporalRequests != null) {
      for (TemporalRequest tr : temporalRequests) {
        String field = tr.timeField();
        String rel = tr.timeRel();
        String time = tr.time();
        String end = tr.endtime();

        System.out.printf("temporalRequests::" + temporalRequests);

        if (field == null || rel == null) continue;

        switch (rel.toLowerCase()) {
          case "between":
            if (time != null && end != null) {
              conditions.add(new Condition(field, Condition.Operator.BETWEEN, List.of(time, end)));
            }
            break;

          case "after":
            if (time != null) {
              conditions.add(new Condition(field, Condition.Operator.GREATER, List.of(time)));
            }
            break;

          case "before":
            if (time != null) {
              conditions.add(new Condition(field, Condition.Operator.LESS, List.of(time)));
            }
            break;

          default:
            // todo: need to thorow an exception here
            LOGGER.warn("Unsupported temporal relation: {}", rel);
        }
      }
    }

    return conditions.isEmpty()
            ? null
            : conditions.stream()
            .reduce((c1, c2) -> new Condition(List.of(c1, c2), Condition.LogicalOperator.AND))
            .get();
  }
}
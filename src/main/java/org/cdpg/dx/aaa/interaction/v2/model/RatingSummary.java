package org.cdpg.dx.aaa.interaction.v2.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record RatingSummary(double averageRating, long totalRatings, Map<String, Long> ratingDistribution) {

  public static RatingSummary empty() {
    Map<String, Long> distribution = new LinkedHashMap<>();
    for (int star = 1; star <= 5; star++) {
      distribution.put(String.valueOf(star), 0L);
    }
    return new RatingSummary(0.0, 0L, distribution);
  }
}

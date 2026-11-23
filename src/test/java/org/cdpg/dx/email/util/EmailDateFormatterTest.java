package org.cdpg.dx.email.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class EmailDateFormatterTest {

  private static final ZoneId IST = EmailDateFormatter.DEFAULT_DISPLAY_ZONE;

  @Test
  void endOfDayIstStoredAsUtcIsShownAsIst() {
    // Reported case: expiry chosen as end of 13-Nov-2026 IST, stored as 18:29:59 UTC.
    assertThat(EmailDateFormatter.formatUtcForDisplay("2026-11-13T18:29:59", IST))
        .isEqualTo("13-Nov-2026 23:59");
  }

  @Test
  void handlesFractionalSecondsFromLocalDateTimeToString() {
    assertThat(EmailDateFormatter.formatUtcForDisplay("2026-11-13T18:29:59.999999", IST))
        .isEqualTo("13-Nov-2026 23:59");
  }

  @Test
  void crossesDateBoundaryWhenConverting() {
    assertThat(EmailDateFormatter.formatUtcForDisplay("2026-11-13T20:00", IST))
        .isEqualTo("14-Nov-2026 01:30");
  }

  @Test
  void blankOrNullGivesEmptyString() {
    assertThat(EmailDateFormatter.formatUtcForDisplay(null, IST)).isEmpty();
    assertThat(EmailDateFormatter.formatUtcForDisplay("  ", IST)).isEmpty();
  }

  @Test
  void unparseableValueIsPassedThrough() {
    assertThat(EmailDateFormatter.formatUtcForDisplay("not-a-date", IST)).isEqualTo("not-a-date");
  }

  @Test
  void otherZonesAreSupported() {
    assertThat(EmailDateFormatter.formatUtcForDisplay("2026-11-13T18:29:59", ZoneId.of("UTC")))
        .isEqualTo("13-Nov-2026 18:29");
  }
}

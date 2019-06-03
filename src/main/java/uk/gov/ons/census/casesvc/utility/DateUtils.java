package uk.gov.ons.census.casesvc.utility;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class DateUtils {
  public OffsetDateTime convertLocalDateTimeToOffsetDateTime(
      LocalDateTime localDateTime, ZoneOffset zoneOffset) {
    return OffsetDateTime.of(localDateTime, zoneOffset);
  }
}

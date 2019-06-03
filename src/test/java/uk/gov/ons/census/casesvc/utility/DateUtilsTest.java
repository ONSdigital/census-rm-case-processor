package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;

public class DateUtilsTest {

  private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private DateUtils underTest;

  @Before
  public void setup() {
    this.underTest = new DateUtils();
  }

  @Test
  public void testConvertLocalDateTimeWithZoneOffset() {
    String expected = "2019-05-31T12:00+01:00";
    LocalDateTime localDateTime = LocalDateTime.parse("2019-05-31 12:00", formatter);

    String actual =
        underTest
            .convertLocalDateTimeToOffsetDateTime(localDateTime, ZoneOffset.ofHours(1))
            .toString();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testConvertLocalDateTimeWithoutZoneOffset() {
    String expected = "2019-05-31T12:00Z";
    LocalDateTime localDateTime = LocalDateTime.parse("2019-05-31 12:00", formatter);

    String actual =
        underTest
            .convertLocalDateTimeToOffsetDateTime(localDateTime, ZoneOffset.ofHours(0))
            .toString();

    assertThat(actual).isEqualTo(expected);
  }
}

package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.junit.Test;

public class LuhnHelperTest {
  LuhnCheckDigit lcd = new LuhnCheckDigit();

  @Test
  public void testCheckDigitWorks() {
    System.out.println(Integer.MAX_VALUE);
    verifyCheckDigit(123, 1230);
    verifyCheckDigit(666, 6668);
    verifyCheckDigit(999, 9993);
    verifyCheckDigit(98765432, 987654324);

    // This are the biggest numbers for an int we would use
    verifyCheckDigit(100000000, 1000000008);
    verifyCheckDigit(214748364, 2147483644);

    // This will work if we use a long for case ref instead of an int
    verifyCheckDigit(999999999, 9999999999L);
  }

  private void verifyCheckDigit(long originalNumber, long numberWithCheckDigit) {
    assertThat(LuhnHelper.addCheckDigit(originalNumber)).isEqualTo(numberWithCheckDigit);
    assertThat(lcd.isValid(Long.toString(numberWithCheckDigit))).isTrue();
    assertThat(checkLuhn(Long.toString(numberWithCheckDigit))).isTrue();
  }

  // This is an "Apache independent" check, to make sure our algorithms agree
  boolean checkLuhn(String number) {
    int nDigits = number.length();

    int nSum = 0;
    boolean isSecond = false;
    for (int i = nDigits - 1; i >= 0; i--) {

      int d = number.charAt(i) - '0';

      if (isSecond == true) d = d * 2;

      // We add two digits to handle
      // cases that make two digits
      // after doubling
      nSum += d / 10;
      nSum += d % 10;

      isSecond = !isSecond;
    }
    return (nSum % 10 == 0);
  }
}

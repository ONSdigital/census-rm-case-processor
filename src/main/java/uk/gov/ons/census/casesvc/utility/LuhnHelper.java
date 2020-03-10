package uk.gov.ons.census.casesvc.utility;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;

public class LuhnHelper {
  public static long addCheckDigit(long number) {
    LuhnCheckDigit lcd = new LuhnCheckDigit();
    try {
      String numberString = Long.toString(number);
      String checkDigit = lcd.calculate(numberString);
      return Long.parseLong(String.format("%s%s", numberString, checkDigit));
    } catch (CheckDigitException e) {
      throw new IllegalArgumentException();
    }
  }
}

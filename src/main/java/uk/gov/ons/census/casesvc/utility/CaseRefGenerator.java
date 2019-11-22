package uk.gov.ons.census.casesvc.utility;

import uk.gov.ons.census.casesvc.utility.pseudorandom.FE1;

public class CaseRefGenerator {
  private static final int LOWEST_POSSIBLE_CASE_REF = 10000000;
  private static final int HIGHEST_POSSIBLE_CASE_REF = 99999999;

  private static final FE1 fe1 =
      new FE1(
          HIGHEST_POSSIBLE_CASE_REF - LOWEST_POSSIBLE_CASE_REF - 1,
          new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06});

  public static int getCaseRef(int sequenceNumber, byte[] hmacKey) {
    int encryptedValue = fe1.encrypt(sequenceNumber, hmacKey);
    return encryptedValue + LOWEST_POSSIBLE_CASE_REF;
  }
}

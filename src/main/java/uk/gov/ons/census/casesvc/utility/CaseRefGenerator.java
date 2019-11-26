package uk.gov.ons.census.casesvc.utility;

import uk.gov.ons.census.casesvc.utility.pseudorandom.PseudorandomNumberGenerator;

public class CaseRefGenerator {
  private static final int LOWEST_POSSIBLE_CASE_REF = 10000000;
  private static final int HIGHEST_POSSIBLE_CASE_REF = 99999999;

  private static final PseudorandomNumberGenerator PSEUDORANDOM_NUMBER_GENERATOR =
      new PseudorandomNumberGenerator(HIGHEST_POSSIBLE_CASE_REF - LOWEST_POSSIBLE_CASE_REF - 1);

  public static int getCaseRef(int sequenceNumber, byte[] hmacKey) {
    // DO NOT replace this with a random number generator - we must have zero collisions/duplicates
    int pseudorandomNumber = PSEUDORANDOM_NUMBER_GENERATOR.getPseudorandom(sequenceNumber, hmacKey);
    return pseudorandomNumber + LOWEST_POSSIBLE_CASE_REF;
  }
}

package uk.gov.ons.census.casesvc.utility;

import java.security.SecureRandom;
import java.util.Random;

public class RandomCaseRefGenerator {
  private static final int LOWEST_POSSIBLE_CASE_REF = 10000000;
  private static final int HIGHEST_POSSIBLE_CASE_REF = 99999999;
  private static final Random random = new SecureRandom();

  public static int testGetCaseRef() {
    int caseRef = random.nextInt(HIGHEST_POSSIBLE_CASE_REF - LOWEST_POSSIBLE_CASE_REF);
    caseRef += LOWEST_POSSIBLE_CASE_REF;
    return caseRef;
  }
}

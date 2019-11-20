package uk.gov.ons.census.casesvc.utility;

import java.math.BigInteger;
import uk.gov.ons.census.casesvc.utility.pseudorandom.FE1;
import uk.gov.ons.census.casesvc.utility.pseudorandom.FPEException;

public class CaseRefGenerator {
  private static final int LOWEST_POSSIBLE_CASE_REF = 10000000;
  private static final int HIGHEST_POSSIBLE_CASE_REF = 99999999;
  // The range of plaintext and ciphertext values - we subtract 1 because 89999999 is prime
  private static final BigInteger modulus =
      BigInteger.valueOf(HIGHEST_POSSIBLE_CASE_REF - LOWEST_POSSIBLE_CASE_REF - 1);

  private static final FE1 fe1 = new FE1();
  // An initialisation vector, or tweak, used in the algorithm.
  private static final byte[] iv = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

  public static int getCaseRef(int sequenceNumber, byte[] hmacKey) {
    BigInteger plaintextValue = BigInteger.valueOf(sequenceNumber);

    try {
      BigInteger encryptedValue = fe1.encrypt(modulus, plaintextValue, hmacKey, iv);
      return encryptedValue.intValue() + LOWEST_POSSIBLE_CASE_REF;
    } catch (FPEException e) {
      throw new RuntimeException("Failure while generating pseudorandom case ref", e);
    }
  }
}

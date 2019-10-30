package uk.gov.ons.census.casesvc.utility;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;
import uk.gov.ons.census.casesvc.utility.pseudorandom.FE1;
import uk.gov.ons.census.casesvc.utility.pseudorandom.FPEException;

public class RandomCaseRefGenerator {
  private static final int LOWEST_POSSIBLE_CASE_REF = 10000000;
  private static final int HIGHEST_POSSIBLE_CASE_REF = 99999999;
  private static final Random random = new SecureRandom();

  public static int getCaseRef() {
    int caseRef = random.nextInt(HIGHEST_POSSIBLE_CASE_REF - LOWEST_POSSIBLE_CASE_REF);
    caseRef += LOWEST_POSSIBLE_CASE_REF;
    return caseRef;
  }

  public static int getPseudoRandomCaseRef(int sequenceNumber) {
    FE1 fe1 = new FE1();

    // The range of plaintext and ciphertext values
    BigInteger modulus =
        BigInteger.valueOf(HIGHEST_POSSIBLE_CASE_REF - LOWEST_POSSIBLE_CASE_REF - 1);

    // A value to encrypt
    BigInteger plaintextValue = BigInteger.valueOf(sequenceNumber);

    // A key, that will be used with the HMAC(SHA256) algorithm, note that this is not secure!
    byte[] hmacKey = new byte[] {0x10, 0x20, 0x10, 0x20, 0x10, 0x20, 0x10, 0x20};

    // An initialisation vector, or tweak, used in the algorithm.
    byte[] iv = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

    try {
      BigInteger encryptedValue = fe1.encrypt(modulus, plaintextValue, hmacKey, iv);
      return encryptedValue.intValue() + LOWEST_POSSIBLE_CASE_REF;
    } catch (FPEException e) {
      throw new RuntimeException("Failure while generating pseudorandom case ref", e);
    }
  }
}

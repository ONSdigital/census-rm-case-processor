package uk.gov.ons.census.casesvc.utility.pseudorandom;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Format Preserving 'Encryption' using the scheme FE1 from the paper "Format-Preserving Encryption"
 * by Bellare, Rogaway, et al (http://eprint.iacr.org/2009/251).
 */
public class PseudorandomNumberGenerator {
  private static final int LOWEST_SAFE_NUMBER_OF_ROUNDS = 3;
  private final int modulus;
  private final int firstFactor;
  private final int secondFactor;
  private final BigInteger firstFactorBi;

  public PseudorandomNumberGenerator(int modulus) {
    this.modulus = modulus;
    int[] factors = NumberTheory.factor(modulus);
    firstFactor = factors[0];
    firstFactorBi = BigInteger.valueOf(firstFactor);
    secondFactor = factors[1];
  }

  /** A simple round function based on SHA-256. */
  private static class FPEEncryptor {

    private byte[] wip;
    private MessageDigest digest;

    /**
     * Initialise a new 'encryptor'.
     *
     * <p>Ultimately this initialises {@link #wip} by creating a byte array with:
     *
     * <ol>
     *   <li>Writing the length of the encoded value of the modulusBI.
     *   <li>Writing the encoded value of the modulusBI.
     *   <li>Writing the length of the key.
     *   <li>Writing the key.
     * </ol>
     *
     * And then setting {@link #wip} to the SHA256'd value of this byte array.
     *
     * @param key the key to 'salt' the SHA256 digest.
     * @param modulus the range of the output numbers.
     */
    private FPEEncryptor(byte[] key, int modulus) {
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("Could not initialise hashing", e);
      }

      byte[] encodedModulus = Utility.convertToByteArrayAndStripLeadingZeros(modulus);

      if (encodedModulus.length > MAX_N_BYTES) {
        throw new IllegalArgumentException(
            "Size of encoded n is too large for FPE encryption (was "
                + encodedModulus.length
                + " bytes, max permitted "
                + MAX_N_BYTES
                + ")");
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
        baos.write(Utility.toBytes(encodedModulus.length));
        baos.write(encodedModulus);

        baos.write(Utility.toBytes(key.length));
        baos.write(key);

        // Flushing most likely a no-op, but best be sure.
        baos.flush();
      } catch (IOException e) {
        // Can't imagine why this would ever happen!
        throw new RuntimeException("Unable to write to byte array output stream!", e);
      }
      wip = digest.digest(baos.toByteArray());
    }

    /**
     * Mixes the round number, the input value r and the previously calculated {@link #wip} in to a
     * new value. Calling this repeatedly on the value of r with a new round number applies a
     * one-way function to the value.
     *
     * <p>Works as follows:
     *
     * <ol>
     *   <li>Serialise r to a minimal byte array with no leading zero bytes
     *   <li>Create a byte array consisting of:
     *       <ol type="i">
     *         <li>macNT value
     *         <li>the round number
     *         <li>the length of the serialised value of r (32-bit)
     *         <li>the value of serialised r
     *       </ol>
     *   <li>Create an SHA256 value of the output array and convert back to a BigInteger, this is
     *       the encrypted r.
     * </ol>
     *
     * @param roundNo to ensure that value is changed in a different way each time, increase this
     *     for each time you call the method on the same value.
     * @param valueToEncrypt the number that we are using as input to the function.
     * @return a new BigInteger value that has reversibly encrypted r.
     */
    private BigInteger oneWayFunction(int roundNo, int valueToEncrypt) {
      byte[] rBin = Utility.convertToByteArrayAndStripLeadingZeros(valueToEncrypt);

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        baos.write(wip);
        baos.write(Utility.toBytes(roundNo));

        baos.write(Utility.toBytes(rBin.length));
        baos.write(rBin);
        byte[] digestBtyes = digest.digest(baos.toByteArray());

        return turnFinalValueIntoPositiveBigInteger(digestBtyes);
      } catch (IOException e) {
        throw new RuntimeException(
            "Unable to write to internal byte array, this should never happen so indicates a defect in the code",
            e);
      }
    }

    private BigInteger turnFinalValueIntoPositiveBigInteger(byte[] encyptedValueBtyes) {
      return new BigInteger(encyptedValueBtyes).abs();
    }
  }

  /** Normally FPE is for SSNs, CC#s, etc.; so limit modulus to 128-bit numbers. */
  private static final int MAX_N_BYTES = 128 / 8;

  /**
   * Generic Z_n FPE 'encryption' using the FE1 scheme.
   *
   * @param originalNumber The number to 'encrypt'. Must not be null.
   * @param key Secret key to 'salt' the hashing with.
   * @return the encrypted version of <code>originalNumber</code>.
   * @throws IllegalArgumentException if any of the parameters are invalid.
   */
  public int getPseudorandom(final int originalNumber, final byte[] key) {

    if (originalNumber > modulus) {
      throw new IllegalArgumentException(
          "Cannot encrypt a number bigger than the modulus (otherwise this wouldn't be format preserving encryption");
    }

    FPEEncryptor encryptor = new FPEEncryptor(key, modulus);

    int pseudorandomNumber = originalNumber;

    /*
     * Apply the same algorithm repeatedly on x for the number of rounds given by getNumberOfRounds. Each round increases the security. Note that the
     * attribute and method names used align to the paper on FE1, not Java conventions on readability.
     */
    for (int round = 0; round < LOWEST_SAFE_NUMBER_OF_ROUNDS; round++) {
      /*
       * Split the value of x in to left and right values (think splitting the binary in to two halves), around the second (smaller) factor
       */
      BigInteger left = BigInteger.valueOf(pseudorandomNumber / secondFactor);
      int right = pseudorandomNumber % secondFactor;

      int w = left.add(encryptor.oneWayFunction(round, right)).mod(firstFactorBi).intValue();
      pseudorandomNumber = firstFactor * right + w;
    }

    return pseudorandomNumber;
  }
}

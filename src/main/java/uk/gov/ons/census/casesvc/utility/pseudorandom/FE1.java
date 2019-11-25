package uk.gov.ons.census.casesvc.utility.pseudorandom;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Format Preserving Encryption using the scheme FE1 from the paper "Format-Preserving Encryption"
 * by Bellare, Rogaway, et al (http://eprint.iacr.org/2009/251).
 */
public class FE1 {
  private static final int LOWEST_SAFE_NUMBER_OF_ROUNDS = 3;
  private final int modulus;
  private final int firstFactor;
  private final int secondFactor;
  private final BigInteger firstFactorBi;
  private final byte[] tweak;

  public FE1(int modulus, byte[] tweak) {
    this.modulus = modulus;
    int[] factors = NumberTheory.factor(modulus);
    firstFactor = factors[0];
    firstFactorBi = BigInteger.valueOf(firstFactor);
    secondFactor = factors[1];
    this.tweak = tweak;
  }

  /** A simple round function based on HMAC(SHA-256). */
  private static class FPEEncryptor {

    /**
     * Using a HMAC over SHA256 for the HMAC algorithm. This must be available on the JVM in order
     * to work.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private byte[] macNT;
    private Mac macGenerator;

    /**
     * Initialise a new encryptor.
     *
     * <p>Requires the availability of the {@value FPEEncryptor#HMAC_ALGORITHM} MAC implementation
     * via JCA.
     *
     * <p>Ultimately this initialises {@link #macNT} by creating a byte array with:
     *
     * <ol>
     *   <li>Writing the length of the encoded value of the modulusBI.
     *   <li>Writing the encoded value of the modulusBI.
     *   <li>Writing the length of the tweak.
     *   <li>Writing the tweak.
     * </ol>
     *
     * And then setting {@link #macNT} to the HMAC'd value of this byte array.
     *
     * @param key the key to initiate the HMAC with SHA256 generator with (must be valid for this
     *     algorithm).
     * @param modulus the range of the output numbers.
     * @param tweak an initialisation vector (IV) that will be used in the initialisation of the
     *     HMAC generator. Must be non-null and length &gt; 0.
     */
    private FPEEncryptor(byte[] key, int modulus, byte[] tweak) {
      try {
        this.macGenerator = Mac.getInstance(HMAC_ALGORITHM);
        this.macGenerator.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      } catch (NoSuchAlgorithmException e) {
        // This should never happen as HMAC/SHA-2 are built in to the JVM.
        throw new IllegalArgumentException(
            HMAC_ALGORITHM + " is not a valid MAC algorithm on this JVM", e);
      } catch (InvalidKeyException e) {
        // Outer class checks that key is more than 1 byte, so don't think this can happen, included
        // for completeness though.
        throw new IllegalArgumentException(
            "The key passed was not valid for use with " + HMAC_ALGORITHM, e);
      }

      if (tweak == null || tweak.length == 0) {
        throw new IllegalArgumentException("tweak (IV) must be an array of length > 0");
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
        baos.write(Utility.toBEBytes(encodedModulus.length));
        baos.write(encodedModulus);

        baos.write(Utility.toBEBytes(tweak.length));
        baos.write(tweak);

        // Flushing most likely a no-op, but best be sure.
        baos.flush();
      } catch (IOException e) {
        // Can't imagine why this would ever happen!
        throw new RuntimeException("Unable to write to byte array output stream!", e);
      }
      this.macNT = this.macGenerator.doFinal(baos.toByteArray());
    }

    /**
     * Mixes the round number, the input value r and the previously calculated {@link #macNT} in to
     * a new value. Calling this repeatedly on the value of r with a new round number applies a
     * reversible encryption to the value.
     *
     * <p>Encryption works as follows:
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
     *   <li>Create an HMAC-SHA256 value of the output array and convert back to a BigInteger, this
     *       is the encrypted r.
     * </ol>
     *
     * @param roundNo to ensure that value is changed in a different way each time, increase this
     *     for each time you call the method on the same value.
     * @param valueToEncrypt the number that we are using as input to the function.
     * @return a new BigInteger value that has reversibly encrypted r.
     */
    private BigInteger applyReversibleEncryption(int roundNo, int valueToEncrypt) {
      byte[] rBin = Utility.convertToByteArrayAndStripLeadingZeros(valueToEncrypt);

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        baos.write(this.macNT);
        baos.write(Utility.toBEBytes(roundNo));

        baos.write(Utility.toBEBytes(rBin.length));
        baos.write(rBin);
        byte[] encyptedValueBtyes = this.macGenerator.doFinal(baos.toByteArray());

        return turnFinalValueIntoPositiveBigInteger(encyptedValueBtyes);
      } catch (IOException e) {
        throw new RuntimeException(
            "Unable to write to internal byte array, this should never happen so indicates a defect in the code",
            e);
      }
    }

    private BigInteger turnFinalValueIntoPositiveBigInteger(byte[] encyptedValueBtyes) {
      byte[] positiveX = new byte[encyptedValueBtyes.length + 1];
      System.arraycopy(encyptedValueBtyes, 0, positiveX, 1, encyptedValueBtyes.length);
      // First byte will always be 0 (default value) so BigInteger will always be positive.
      return new BigInteger(positiveX);
    }
  }

  /** Normally FPE is for SSNs, CC#s, etc.; so limit modulus to 128-bit numbers. */
  private static final int MAX_N_BYTES = 128 / 8;

  /**
   * Generic Z_n FPE encryption using the FE1 scheme.
   *
   * @param plaintext The number to encrypt. Must not be null.
   * @param key Secret key to encrypt with. Must be compatible with HMAC(SHA256). See
   *     https://tools.ietf.org/html/rfc2104#section-3 for recommendations, on key length and
   *     generation. Anything over 64 bytes is hashed to a 64 byte value, 32 bytes is generally
   *     considered "good enough" for most applications.
   * @return the encrypted version of <code>plaintext</code>.
   * @throws IllegalArgumentException if any of the parameters are invalid.
   */
  public int encrypt(final int plaintext, final byte[] key) {

    if (plaintext > modulus) {
      throw new IllegalArgumentException(
          "Cannot encrypt a number bigger than the modulus (otherwise this wouldn't be format preserving encryption");
    }

    FPEEncryptor encryptor = new FPEEncryptor(key, modulus, tweak);

    int encryptingText = plaintext;

    /*
     * Apply the same algorithm repeatedly on x for the number of rounds given by getNumberOfRounds. Each round increases the security. Note that the
     * attribute and method names used align to the paper on FE1, not Java conventions on readability.
     */
    for (int round = 0; round < LOWEST_SAFE_NUMBER_OF_ROUNDS; round++) {
      /*
       * Split the value of x in to left and right values (think splitting the binary in to two halves), around the second (smaller) factor
       */
      BigInteger left = BigInteger.valueOf(encryptingText / secondFactor);
      int right = encryptingText % secondFactor;

      BigInteger w = left.add(encryptor.applyReversibleEncryption(round, right)).mod(firstFactorBi);
      encryptingText = firstFactor * right + w.intValue();
    }

    return encryptingText;
  }
}

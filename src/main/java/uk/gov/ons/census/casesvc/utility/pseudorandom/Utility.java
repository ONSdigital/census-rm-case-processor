package uk.gov.ons.census.casesvc.utility.pseudorandom;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods that swap between BigInteger, Java primitives and byte array representations of
 * numbers.
 */
class Utility {

  static byte[] convertToByteArrayAndStripLeadingZeros(int n) {
    byte[] encodedN;
    if (n == 0) {
      encodedN = new byte[0];
    } else {
      byte[] nAsByteArray = Utility.toBEBytes(n);
      int firstNonZeroIndex = 0;

      while ((nAsByteArray[firstNonZeroIndex] == 0) && (firstNonZeroIndex < nAsByteArray.length)) {
        firstNonZeroIndex++;
      }
      encodedN = new byte[nAsByteArray.length - firstNonZeroIndex];
      System.arraycopy(
          nAsByteArray, firstNonZeroIndex, encodedN, 0, nAsByteArray.length - firstNonZeroIndex);
    }

    return encodedN;
  }

  /**
   * Returns a byte array representing the 32-bit integer passed in a 4 element byte array in
   * big-endian form.
   *
   * @param i the integer to create the byte array for
   * @return the 4 element byte array. Never null.
   */
  static byte[] toBEBytes(int i) {
    return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(i).array();
  }

  /** Prevents construction of a utility class. */
  private Utility() {}
}

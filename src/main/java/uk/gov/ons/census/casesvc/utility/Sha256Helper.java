package uk.gov.ons.census.casesvc.utility;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256Helper {
  private static final MessageDigest digest;

  static {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException();
    }
  }

  public static String hash(String stringToHash) {
    byte[] hash;

    // Digest is not thread safe
    synchronized (digest) {
      hash = digest.digest(stringToHash.getBytes(StandardCharsets.UTF_8));
    }

    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }
}

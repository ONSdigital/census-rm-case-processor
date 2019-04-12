package uk.gov.ons.census.casesvc.utility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QidCreator {

  @Value("${qid.modulus}")
  private int modulus;

  @Value("${qid.factor}")
  private int factor;

  public long createQid(int questionnaireType, int trancheIdentifier, long uniqueNumber) {
    String sourceDigits =
        String.format("%02d%01d%011d", questionnaireType, trancheIdentifier, uniqueNumber);

    int checkDigits = calculateCheckDigits(sourceDigits);

    return Long.parseLong(String.format("%s%02d", sourceDigits, checkDigits));
  }

  private int calculateCheckDigits(String sourceDigits) {
    int remainder = sourceDigits.charAt(0);

    for (int charIdx = 1; charIdx < sourceDigits.length(); charIdx++) {
      int temp = sourceDigits.charAt(charIdx);
      remainder = (remainder * factor + temp) % modulus;
    }
    return remainder % modulus;
  }
}

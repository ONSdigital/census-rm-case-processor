package uk.gov.ons.census.casesvc.utility;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QidCreator {
  private static final Logger log = LoggerFactory.getLogger(QidCreator.class);

  @Value("${qid.modulus}")
  private int modulus;

  @Value("${qid.factor}")
  private int factor;

  @Value("${qid.tranche-identifier}")
  private int trancheIdentifier;

  public String createQid(int questionnaireType, long uniqueNumber) {
    String sourceDigits =
        String.format("%02d%01d%011d", questionnaireType, trancheIdentifier, uniqueNumber);

    int checkDigits = calculateCheckDigits(sourceDigits);
    if (checkDigits > 99) {
      log.with("check_digits", checkDigits)
          .with("modulus", modulus)
          .with("factor", factor)
          .error("Check Digits too long, must be 99 or less");
      throw new IllegalStateException();
    }
    return String.format("%s%02d", sourceDigits, checkDigits);
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

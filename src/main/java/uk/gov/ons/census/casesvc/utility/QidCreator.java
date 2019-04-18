package uk.gov.ons.census.casesvc.utility;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QidCreator {
  private static final Logger log = LoggerFactory.getLogger(QidCreator.class);

  private static final String UNKNOWN_COUNTRY_ERROR = "Unknown Country";
  private static final String UNEXPECTED_CASE_TYPE_ERROR = "Unexpected Case Type";

  @Value("${qid.modulus}")
  private int modulus;

  @Value("${qid.factor}")
  private int factor;

  @Value("${qid.tranche-identifier}")
  private int trancheIdentifier;

  public String createQid(String treatmentCode, long uniqueNumber) {
    int questionnaireType = calculateQuestionnaireType(treatmentCode);
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

  private int calculateQuestionnaireType(String treatmentCode) {
    String country = treatmentCode.substring(treatmentCode.length() - 1);
    if (!country.equals("E") && !country.equals("W") && !country.equals("N")) {
      log.with("treatment_code", treatmentCode).error(UNKNOWN_COUNTRY_ERROR);
      throw new IllegalArgumentException();
    }

    if (treatmentCode.startsWith("HH")) {
      switch (country) {
        case "E":
          return 1;
        case "W":
          return 2;
        case "N":
          return 4;
      }
    } else if (treatmentCode.startsWith("CI")) {
      switch (country) {
        case "E":
          return 21;
        case "W":
          return 22;
        case "N":
          return 24;
      }
    } else if (treatmentCode.startsWith("CE")) {
      switch (country) {
        case "E":
          return 31;
        case "W":
          return 32;
        case "N":
          return 34;
      }
    } else {
      log.with("treatment_code", treatmentCode).error(UNEXPECTED_CASE_TYPE_ERROR);
      throw new IllegalArgumentException();
    }

    throw new RuntimeException(); // This code should be unreachable
  }
}

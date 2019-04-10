package uk.gov.ons.census.casesvc.utility;

import org.springframework.stereotype.Component;

@Component
public class QidCreator {

  public long createQid(int questionnaireType, int trancheIdentifier, long uniqueNumber) {

    int checkDigits = 99;

    return Long.parseLong(
        String.format(
            "%02d%01d%011d%02d", questionnaireType, trancheIdentifier, uniqueNumber, checkDigits));
  }
}

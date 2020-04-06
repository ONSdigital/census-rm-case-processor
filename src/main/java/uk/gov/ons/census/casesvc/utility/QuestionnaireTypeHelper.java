package uk.gov.ons.census.casesvc.utility;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class QuestionnaireTypeHelper {
  private static final Logger log = LoggerFactory.getLogger(QuestionnaireTypeHelper.class);

  private static final String ADDRESS_LEVEL_ESTAB = "E";

  private static final String COUNTRY_CODE_ENGLAND = "E";
  private static final String COUNTRY_CODE_WALES = "W";
  private static final String COUNTRY_CODE_NORTHERN_IRELAND = "N";

  private static final String CASE_TYPE_HOUSEHOLD = "HH";
  private static final String CASE_TYPE_SPG = "SPG";
  private static final String CASE_TYPE_CE = "CE";

  private static final String UNEXPECTED_CASE_TYPE_ERROR = "Unexpected Case Type";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND = "21";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_ENGLISH = "22";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_WELSH = "23";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_NORTHERN_IRELAND = "24";
  private static final Set<String> individualQuestionnaireTypes =
      new HashSet<>(
          Arrays.asList(
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND,
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_ENGLISH,
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_WELSH,
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_NORTHERN_IRELAND));

  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";
  private static final String WALES_HOUSEHOLD_CONTINUATION = "12";
  private static final String WALES_HOUSEHOLD_CONTINUATION_WELSH = "13";
  private static final String NORTHERN_IRELAND_HOUSEHOLD_CONTINUATION = "14";
  private static final String CCS_POSTBACK_CONTINUATION_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES = "61";
  private static final String CCS_POSTBACK_CONTINUATION_QUESTIONNAIRE_FOR_WALES_WELSH = "63";
  private static final Set<String> continutationQuestionnaireTypes =
      new HashSet<>(
          Arrays.asList(
              ENGLAND_HOUSEHOLD_CONTINUATION,
              WALES_HOUSEHOLD_CONTINUATION,
              WALES_HOUSEHOLD_CONTINUATION_WELSH,
              NORTHERN_IRELAND_HOUSEHOLD_CONTINUATION,
              CCS_POSTBACK_CONTINUATION_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES,
              CCS_POSTBACK_CONTINUATION_QUESTIONNAIRE_FOR_WALES_WELSH));

  public static int calculateQuestionnaireType(
      String caseType, String region, String addressLevel) {
    String country = region.substring(0, 1);
    if (!country.equals(COUNTRY_CODE_ENGLAND)
        && !country.equals(COUNTRY_CODE_WALES)
        && !country.equals(COUNTRY_CODE_NORTHERN_IRELAND)) {
      throw new IllegalArgumentException(
          String.format("Unknown Country for treatment code %s", caseType));
    }

    if (isCeCaseType(caseType) && addressLevel.equals("U")) {
      switch (country) {
        case COUNTRY_CODE_ENGLAND:
          return 21;
        case COUNTRY_CODE_WALES:
          return 22;
        case COUNTRY_CODE_NORTHERN_IRELAND:
          return 24;
      }
    } else if (isHouseholdCaseType(caseType) || isSpgCaseType(caseType)) {
      switch (country) {
        case COUNTRY_CODE_ENGLAND:
          return 1;
        case COUNTRY_CODE_WALES:
          return 2;
        case COUNTRY_CODE_NORTHERN_IRELAND:
          return 4;
      }
    } else if (isCE1RequestForEstabCeCase(caseType, addressLevel)) {
      switch (country) {
        case COUNTRY_CODE_ENGLAND:
          return 31;
        case COUNTRY_CODE_WALES:
          return 32;
        case COUNTRY_CODE_NORTHERN_IRELAND:
          return 34;
      }
    } else {
      throw new IllegalArgumentException(
          String.format("Unexpected Case Type for treatment code %s", caseType));
    }

    throw new RuntimeException(String.format("Unprocessable treatment code '%s'", caseType));
  }

  public static boolean isQuestionnaireWelsh(String treatmentCode) {
    return (treatmentCode.startsWith("HH_Q") && treatmentCode.endsWith("W"));
  }

  public static boolean isIndividualQuestionnaireType(String questionnaireId) {
    String questionnaireType = questionnaireId.substring(0, 2);
    return individualQuestionnaireTypes.contains(questionnaireType);
  }

  private static boolean isCE1RequestForEstabCeCase(String caseType, String addressLevel) {
    return isCeCaseType(caseType) && addressLevel.equals(ADDRESS_LEVEL_ESTAB);
  }

  private static boolean isSpgCaseType(String caseType) {
    return caseType.startsWith(CASE_TYPE_SPG);
  }

  private static boolean isHouseholdCaseType(String caseType) {
    return caseType.startsWith(CASE_TYPE_HOUSEHOLD);
  }

  private static boolean isCeCaseType(String caseType) {
    return caseType.startsWith(CASE_TYPE_CE);
  }
}

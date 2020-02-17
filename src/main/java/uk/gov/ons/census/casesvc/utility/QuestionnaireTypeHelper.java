package uk.gov.ons.census.casesvc.utility;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class QuestionnaireTypeHelper {
  private static final Logger log = LoggerFactory.getLogger(QuestionnaireTypeHelper.class);

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

  public static int calculateQuestionnaireType(String treatmentCode) {
    String country = treatmentCode.substring(treatmentCode.length() - 1);
    if (!country.equals("E") && !country.equals("W") && !country.equals("N")) {
      throw new IllegalArgumentException(
          String.format("Unknown Country for treatment code %s", treatmentCode));
    }

    if (treatmentCode.startsWith("HH") || treatmentCode.startsWith("SPG")) {
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
      throw new IllegalArgumentException(
          String.format("Unexpected Case Type for treatment code %s", treatmentCode));
    }

    throw new RuntimeException(String.format("Unprocessable treatment code '%s'", treatmentCode));
  }

  public static boolean isQuestionnaireWelsh(String treatmentCode) {
    return (treatmentCode.startsWith("HH_Q") && treatmentCode.endsWith("W"));
  }

  public static boolean isIndividualQuestionnaireType(String questionnaireId) {
    String questionnaireType = questionnaireId.substring(0, 2);
    return individualQuestionnaireTypes.contains(questionnaireType);
  }

  public static boolean iscontinuationQuestionnaireTypes(String questionnaireId) {
    String questionnaireType = questionnaireId.substring(0, 2);
    return continutationQuestionnaireTypes.contains(questionnaireType);
  }
}

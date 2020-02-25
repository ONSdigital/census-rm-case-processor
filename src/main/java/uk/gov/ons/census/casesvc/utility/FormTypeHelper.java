package uk.gov.ons.census.casesvc.utility;

public class FormTypeHelper {
  private static final String HH_FORM_TYPE = "H";
  private static final String IND_FORM_TYPE = "I";
  private static final String CE1_FORM_TYPE = "C";
  private static final String CONTINUATION = "Cont";

  public static String mapQuestionnaireTypeToFormType(String qid) {
    int questionnaireType = Integer.parseInt(qid.substring(0, 2));

    switch (questionnaireType) {
      case 1:
      case 2:
      case 3:
      case 4:
        return HH_FORM_TYPE;
      case 11:
      case 12:
      case 13:
      case 14:
        return CONTINUATION;
      case 21:
      case 22:
      case 23:
      case 24:
        return IND_FORM_TYPE;
      case 31:
      case 32:
      case 33:
      case 34:
        return CE1_FORM_TYPE;
      case 51:
      case 52:
      case 53:
      case 54:
        return HH_FORM_TYPE;
      case 61:
      case 63:
        return CONTINUATION;
      case 71:
      case 72:
      case 73:
      case 74:
        return HH_FORM_TYPE;
      case 81:
      case 82:
      case 83:
      case 84:
        return CE1_FORM_TYPE;
      default:
        return null;
    }
  }
}

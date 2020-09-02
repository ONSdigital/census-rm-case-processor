package uk.gov.ons.census.casesvc.utility;

import uk.gov.ons.census.casesvc.model.entity.Case;

public class FieldworkHelper {
  public static boolean shouldSendCaseToField(Case caze, String eventChannel) {
    if (eventChannel.equals("FIELD")) {
      return false;
    }
    if (caze.getRegion().startsWith("N") && caze.getCaseType().equals("CE")) {
      return false;
    }
    if (caze.getEstabType().equals("TRANSIENT PERSONS")
        || caze.getEstabType().equals("MIGRANT WORKERS")) {
      return false;
    }
    return true;
  }
}

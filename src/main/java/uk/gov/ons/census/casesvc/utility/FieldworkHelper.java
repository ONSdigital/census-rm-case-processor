package uk.gov.ons.census.casesvc.utility;

import uk.gov.ons.census.casesvc.model.entity.Case;

public class FieldworkHelper {
  public static boolean shouldSendUpdatedCaseToField(Case caze, String eventChannel) {
    if (hasChannelField(eventChannel)) {
      return false;
    }

    if (hasRegionNorthernIrelandAndCECaseType(caze)) {
      return false;
    }

    if (caze.getEstabType().equals("TRANSIENT PERSONS")
        || caze.getEstabType().equals("MIGRANT WORKERS")) {
      return false;
    }
    return true;
  }

  public static boolean shouldSendRevalidateAddressCaseToField(Case caze, String eventChannel) {
    if (hasChannelField(eventChannel)) {
      return false;
    }

    if (hasRegionNorthernIrelandAndCECaseType(caze)) {
      return false;
    }

    if (caze.getRefusalReceived() != null) {
      return false;
    }

    if (caze.getEstabType() != null) {
      return !caze.getEstabType().equals("TRANSIENT PERSONS");
    }
    return true;
  }

  private static boolean hasRegionNorthernIrelandAndCECaseType(Case caze) {
    return caze.getRegion().startsWith("N") && caze.getCaseType().equals("CE");
  }

  private static boolean hasChannelField(String eventChannel) {
    return eventChannel.equals("FIELD");
  }
}

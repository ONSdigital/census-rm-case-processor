package uk.gov.ons.census.casesvc.utility;

import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.model.entity.Case;

public class FieldworkHelper {
  public static boolean shouldSendCaseToField(Case caze) {

    if (StringUtils.isEmpty(caze.getEstabType())
        || caze.getEstabType().equals("TRANSIENT PERSONS")) {
      return false;
    }

    if (caze.getRegion().startsWith("N") && caze.getCaseType().equals("CE")) {
      return false;
    }

    if (caze.isAddressInvalid()) {
      return false;
    }

    if (caze.getRefusalReceived() != null) {
      return false;
    }

    //    Case is marked as receipted AND case is not a CE E
    if (caze.isReceiptReceived()) {
      if (!caze.getCaseType().equals("CE")) {
        return false;
      }

      if (!caze.getAddressLevel().equals("E")) {
        return false;
      }
    }

    if (caze.isReceiptReceived()
        && caze.getCaseType().equals("CE")
        && caze.getAddressLevel().equals("E")
        && (caze.getCeActualResponses() >= caze.getCeExpectedCapacity())) {
      return false;
    }

    if (caze.getCaseType().equals("HI")) {
      return false;
    }

    if (StringUtils.isEmpty(caze.getFieldOfficerId())
        && (caze.getCaseType().equals("CE") || caze.getCaseType().equals("SPG"))) {
      return false;
    }

    if (StringUtils.isEmpty(caze.getFieldCoordinatorId())) {
      return false;
    }

    //    if (StringUtils.isEmpty(caze.getOa())) {
    //      return false;
    //    }

    if (StringUtils.isEmpty(caze.getLatitude()) || StringUtils.isEmpty(caze.getLongitude())) {
      return false;
    }

    if (StringUtils.isEmpty(caze.getEstabUprn()) && caze.getCaseType().equals("CE")) {
      return false;
    }

    return true;
  }
}

package uk.gov.ons.census.casesvc.builders;

import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.CcsToField;
import uk.gov.ons.census.casesvc.model.entity.Case;

@Component
public class CcsToFieldBuilder {

  public CcsToField buildCcsToField(Case caze, String actionPlan, String actionType) {

    CcsToField ccsToField = new CcsToField();
    ccsToField.setAddressLine1(caze.getAddressLine1());
    ccsToField.setAddressLine2(caze.getAddressLine2());
    ccsToField.setAddressLine3(caze.getAddressLine3());
    ccsToField.setTownName(caze.getTownName());
    ccsToField.setPostcode(caze.getPostcode());
    ccsToField.setEstabType(caze.getEstabType());
    ccsToField.setLatitude(caze.getLatitude());
    ccsToField.setLongitude(caze.getLongitude());
    ccsToField.setCaseId(caze.getCaseId().toString());
    ccsToField.setCaseRef(Integer.toString(caze.getCaseRef()));
    ccsToField.setAddressType(caze.getAddressType());
    ccsToField.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    ccsToField.setUndeliveredAsAddress(false);

    // TODO: set surveyName, undeliveredAsAddress and blankQreReturned from caze
    ccsToField.setSurveyName("CCS");
    ccsToField.setBlankQreReturned(false);

    // TODO: ccsQuestionnaireUrl, ceDeliveryReqd,
    // ceCE1Complete, ceActualResponses

    return ccsToField;
  }
}

package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class CcsToFwmt {
  private String actionPlan;
  private String actionType;
  private String addressLine1;
  private String addressLine2;
  private String addressLine3;
  private String townName;
  private String postcode;
  private String estabType;
  private String latitude;
  private String longitude;
  private String caseId;
  private String caseRef;
  private String addressType;
  private String fieldCoordinatorId;
  private String surveyName;
  private Boolean undeliveredAsAddress;
  private Boolean blankQreReturned;
}

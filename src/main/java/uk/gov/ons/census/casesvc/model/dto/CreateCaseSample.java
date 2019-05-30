package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class CreateCaseSample {
  private String arid;
  private String estabArid;
  private String uprn;
  private String addressType;
  private String estabType;
  private String addressLevel;
  private String abpCode;
  private String organisationName;
  private String addressLine1;
  private String addressLine2;
  private String addressLine3;
  private String townName;
  private String postcode;
  private String latitude;
  private String longitude;
  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String region;
  private String htcWillingness;
  private String htcDigital;
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private String treatmentCode;
  private String ceExpectedCapacity;
  private String collectionExerciseId;
  private String actionPlanId;
}

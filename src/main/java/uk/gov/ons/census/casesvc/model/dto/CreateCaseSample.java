package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class CreateCaseSample {
  String arid;
  String estabArid;
  String uprn;
  String addressType;
  String estabType;
  String addressLevel;
  String abpCode;
  String organisationName;
  String addressLine1;
  String addressLine2;
  String addressLine3;
  String townName;
  String postcode;
  String latitude;
  String longitude;
  String oa;
  String lsoa;
  String msoa;
  String lad;
  String rgn;
  String htcWillingness;
  String htcDigital;
  String treatmentCode;
  String collectionExerciseId;
  String actionPlanId;
}

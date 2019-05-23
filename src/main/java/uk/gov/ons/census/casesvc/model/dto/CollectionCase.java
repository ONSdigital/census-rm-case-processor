package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class CollectionCase {
  private String id;
  private String caseRef;
  private String survey;
  private String collectionExerciseId;
  private Address address;
  private String state;
  private String actionableFrom;

  //TODO, Are these for RH too, only important regarding where they go in this file, nowt functional
  private String fieldCoordinatorId;
  private String fieldOfficerId;
  private String ceExpectedCapacity;

  // Below this line is extra data potentially needed by Action Scheduler - can be ignored by RH
  private String actionPlanId;
  private String treatmentCode;
  private String oa;
  private String lsoa;
  private String msoa;
  private String lad;
  private String htcWillingness;
  private String htcDigital;
}

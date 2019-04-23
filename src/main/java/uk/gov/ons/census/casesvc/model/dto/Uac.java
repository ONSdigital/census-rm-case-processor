package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Uac {
  String uacHash;
  String uac;
  boolean active;
  String questionnaireId;
  String caseType;
  String region;
  String caseId;
  String collectionExerciseId;
}

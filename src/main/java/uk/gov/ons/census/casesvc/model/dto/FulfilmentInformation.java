package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class FulfilmentInformation {
  String productCode;
  String caseRef;
  String questionnaireId;
}

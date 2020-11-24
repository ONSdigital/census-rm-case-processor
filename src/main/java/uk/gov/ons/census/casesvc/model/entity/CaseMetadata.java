package uk.gov.ons.census.casesvc.model.entity;

import lombok.Data;

@Data
public class CaseMetadata {

  private Boolean secureEstablishment;
  private String channel;
  private NonComplianceType nonCompliance;
}

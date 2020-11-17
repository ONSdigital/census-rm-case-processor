package uk.gov.ons.census.casesvc.model.entity;

import lombok.Data;
import uk.gov.ons.census.casesvc.model.dto.NonComplianceTypeDTO;

@Data
public class CaseMetadata {

  private Boolean secureEstablishment;
  private String channel;
  private NonComplianceTypeDTO nonCompliance;
}

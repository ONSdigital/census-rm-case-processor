package uk.gov.ons.census.casesvc.model.entity;

import lombok.Data;
import uk.gov.ons.census.casesvc.model.dto.NoneCompliancelTypeDTO;

@Data
public class CaseMetadata {

  private Boolean secureEstablishment;
  private String channel;
  private NoneCompliancelTypeDTO noneCompliance;
}

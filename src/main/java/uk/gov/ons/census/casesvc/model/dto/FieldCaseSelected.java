package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class FieldCaseSelected {
  private long caseRef;
  private UUID actionRuleId;
}
